package servicenow.core;

/**
 * Contains data type information for a single field in a ServiceNow table.
 * @author Giles Lewis
 *
 */
public class FieldDefinition {

	private final Table table;
	private final String name;
	private final String type;
	private final Integer max_length;
	private final String ref_table;
	public static final FieldNames DICT_FIELDS = new FieldNames("element,internal_type,max_length,reference");
	
	/**
	 * Construct a FieldDefinition from sys_dictionary record.
	 * 
	 * @param table - The table in which this field appears.
	 * @param dictrec - The sys_dictionary record that describes this field.
	 */
	protected FieldDefinition(Table table, Record dictrec) {
		this.table = table;
		this.name = dictrec.getValue("element");
		this.type = dictrec.getValue("internal_type");
		this.max_length = dictrec.getInteger("max_length");
		this.ref_table = dictrec.getValue("reference");
		if (name == null)
			throw new AssertionError(String.format(
				"Missing name for field in \"%s\". Check sys_dictionary read permissions.", table.getName()));
		if (type == null) 
			throw new AssertionError(String.format(
				"Field \"%s.%s\" has no type. Check sys_dictionary read permissions.", table.getName(), name));  
//		assert name != null : "Field has no name";
//		assert type != null : "Field " + name + " has no type";
	}

	/**
	 * Return the table
	 */
	public Table getTable() {
		return table;
	}
	
	/**
	 * Return the name of this field.
	 */
	public String getName() { 
		return name; 
	}
	
	/**
	 * Return the type of this field.
	 */
	public String getType() { 
		return type; 
	}
	
	/**
	 * Return the length of this field.
	 */
	public int getLength() { 
		return max_length; 
	}
	
	/**
	 * If this is a reference field then return the name of the
	 * referenced table.  Otherwise return null.
	 */
	public String getReference() { 
		return ref_table; 
	}
	
	/**
	 * Return true if the field is a reference field.
	 * The value of a reference field is always a {@link Key} (sys_id).
	 */
	public boolean isReference() { 
		return (ref_table != null && ref_table.length() > 0); 
	}
		
}
