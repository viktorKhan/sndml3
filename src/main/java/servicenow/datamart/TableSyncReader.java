package servicenow.datamart;

import servicenow.api.*;

import java.io.IOException;
import java.sql.SQLException;

public class TableSyncReader extends TableReader {

	final Database db;
	final String sqlTableName;
	final WriterMetrics writerMetrics = new WriterMetrics();
	TimestampHash dbTimestamps;
	RecordList snTimestamps;
	KeySet insertSet;
	KeySet updateSet;
	KeySet deleteSet;
	KeySet skipSet;
	
	public TableSyncReader(Table table, Database db, String sqlTableName) {
		super(table);
		this.db = db;
		this.sqlTableName = sqlTableName;
	}

	@Override
	public int getDefaultPageSize() {
		return 1000;
	}

	@Override
	public WriterMetrics getWriterMetrics() {
		return this.writerMetrics;
	}
	
	public void initialize(DateTimeRange createdRange) 
			throws IOException, SQLException, InterruptedException {
		super.initialize();
		logger.debug(Log.INIT, 
				"initialize table=" + table.getName() + " created=" + createdRange);
		DatabaseTimestampReader dbtsr = new DatabaseTimestampReader(db);
		if (createdRange == null) 
			dbTimestamps = dbtsr.getTimestamps(sqlTableName);
		else
			dbTimestamps = dbtsr.getTimestamps(sqlTableName, createdRange);
		logger.debug(Log.INIT, String.format("database rows=%d", dbTimestamps.size()));
		RestTableReader sntsr = new RestTableReader(this.table);
		sntsr.setFields(new FieldNames("sys_id,sys_updated_on"));
		sntsr.setCreatedRange(createdRange);
		sntsr.setPageSize(10000);
		sntsr.enableStats(false);
		sntsr.initialize();
		snTimestamps = sntsr.getAllRecords();
		TimestampHash examined = new TimestampHash();
		insertSet = new KeySet();
		updateSet = new KeySet();
		deleteSet = new KeySet();
		skipSet = new KeySet();
		for (Record rec : snTimestamps) {
			Key key = rec.getKey();
			DateTime snts = rec.getUpdatedTimestamp();
			DateTime dbts = dbTimestamps.get(key);
			if (dbts == null)
				insertSet.add(key);
			else if (dbts.equals(snts))
				skipSet.add(key);
			else
				updateSet.add(key);
			examined.put(key, snts);
		}
		logger.debug(Log.INIT, String.format("inserts=%d updated=%d skips=%d", 
				insertSet.size(), updateSet.size(), skipSet.size()));
		assert examined.size() == (insertSet.size() + updateSet.size() + skipSet.size()); 
		for (Key key : dbTimestamps.keySet()) {
			if (examined.get(key) == null) 
				deleteSet.add(key);
		}
		logger.info(Log.INIT, String.format(
			"compare identified %d inserts, %d updates, %d deletes, %d skips", 
			insertSet.size(), updateSet.size(), deleteSet.size(), skipSet.size()));
		int expected = insertSet.size() + updateSet.size() + deleteSet.size();
		assert examined.size() == (expected + skipSet.size());
		this.setExpected(expected);
	}

	@Override
	public TableReader setBaseQuery(EncodedQuery value) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public TableReader call() throws IOException, SQLException, InterruptedException {
		assert initialized;
		assert dbTimestamps != null;
		assert snTimestamps != null;
		// Process the Inserts
		setLogContext();
		logger.info(Log.PROCESS, String.format("Inserting %d rows", insertSet.size()));
		DatabaseInsertWriter insertWriter = new DatabaseInsertWriter(db, table, sqlTableName);
		KeySetTableReader insertReader = new KeySetTableReader(table);
		insertReader.setParent(this);
		insertReader.setWriter(insertWriter.open());
		insertReader.initialize(insertSet);
		insertReader.call();
		insertWriter.close();
		writerMetrics.add(insertWriter.getMetrics());
		if (insertWriter.getMetrics().getInserted() != insertSet.size())
			logger.error(Log.PROCESS, String.format("inserted %d, expected to insert %d", 
				insertWriter.getMetrics().getInserted(), insertSet.size()));
		
		// Process the Updates
		setLogContext();
		logger.info(Log.PROCESS, String.format("Updating %d rows",  updateSet.size()));
		DatabaseUpdateWriter updateWriter = new DatabaseUpdateWriter(db, table, sqlTableName);
		KeySetTableReader updateReader = new KeySetTableReader(table);
		updateReader.setParent(this);
		updateReader.setWriter(updateWriter.open());
		updateReader.initialize(updateSet);
		updateReader.call();
		updateWriter.close();
		writerMetrics.add(updateWriter.getMetrics());
		if (updateWriter.getMetrics().getUpdated() != updateSet.size())
			logger.error(Log.PROCESS, String.format("updated %d, expected to update %d", 
				updateWriter.getMetrics().getUpdated(), updateSet.size()));
					
		// Process the Deletes
		setLogContext();
		logger.info(Log.PROCESS, String.format("Deleting %d rows", deleteSet.size()));
		DatabaseDeleteWriter deleteWriter = new DatabaseDeleteWriter(db, table, sqlTableName);
		deleteWriter.open();
		for (Key key : deleteSet) {
			deleteWriter.deleteRecord(key);
		}
		deleteWriter.close();
		writerMetrics.add(deleteWriter.getMetrics());
		if (deleteWriter.getMetrics().getDeleted() != deleteSet.size())
			logger.error(Log.PROCESS, String.format("deleted %d, expected to delete %d", 
				deleteWriter.getMetrics().getDeleted(), deleteSet.size()));
		writerMetrics.addSkipped(skipSet.size());
		return this;
	}

}