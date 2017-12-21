package servicenow.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWriter extends Writer {

	public enum Format {Import, List};
	
	File file;
	PrintWriter writer;

	Format format = Format.Import; 

	Logger logger = LoggerFactory.getLogger(this.getClass());
	
	public static void main(String[] args) throws Exception {
		Log.setGlobalContext();
		Options options = new Options();
		options.addOption(Option.builder("p").longOpt("prop").required(true).hasArg(true).
				desc("Profile file name (required)").build());
		options.addOption(Option.builder("t").longOpt("table").required(true).hasArg(true).
				desc("Table name").build());
		options.addOption(Option.builder("o").longOpt("output").required(false).hasArg(true).
				desc("Output file name").build());
		options.addOption(Option.builder("q").longOpt("query").required(false).hasArg(true).
				desc("Encoded query").build());
		DefaultParser parser = new DefaultParser();
		CommandLine cmdline = parser.parse(options,  args);
		String propfilename = cmdline.getOptionValue("p");
		String outfilename = cmdline.getOptionValue("o");
		String tablename = cmdline.getOptionValue("t");
		String querystring = cmdline.getOptionValue("q");
		Properties props = new Properties();
		File propfile = new File(propfilename);
		props.load(new FileInputStream(propfile));
		File outfile = (outfilename == null) ? null : new File(outfilename);
		Session session = new Session(props);
		Table table = session.table(tablename);
		FileWriter writer = new FileWriter(outfile);
		TableReader reader = table.getDefaultReader().setWriter(writer);
		reader.setBaseQuery(querystring == null ? EncodedQuery.all() : new EncodedQuery(querystring));
		writer.open();
		reader.call();
		writer.close();
	}

	public FileWriter(File file) {
		this.file = file;
	}

	public FileWriter setFormat(Format fmt) {
		this.format = fmt;
		return this;
	}
	
	@Override
	public void open() throws IOException {
		metrics.start();
		if (file == null)
			writer = new PrintWriter(System.out);
		else
			writer = new PrintWriter(file);
		if (format == Format.List) {
			writer.println("[");
			writer.flush();
		}
	}

	@Override
	public synchronized void processRecords(RecordList recs) {
		assert writer != null;
		assert recs != null;
		for (Record rec : recs) {
			processRecord(rec);
		}
	}

	public void processRecord(Record rec) {		
		assert writer != null;
		assert rec !=null;
		JSONWriter json = new JSONWriter(writer);
		json.object();
		for (String name : rec.getFieldNames()) {
			String value = rec.getValue(name);
			if (value != null && value.length() > 0) {
				json.key(name);
				json.value(value);				
			}
		}
		json.endObject();
		if (format == Format.Import)
			writer.println();
		else
			writer.println(",");
		writer.flush();
		metrics.incrementInserted();
	}

	@Override
	public void close() {
		if (format == Format.List) writer.println("]");
		writer.close();
		metrics.finish();
	}
	
}
