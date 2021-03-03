/*
 * This file is generated by jOOQ.
 */
package edu.utexas.tacc.tapis.systems.gen.jooq.tables;


import edu.utexas.tacc.tapis.systems.gen.jooq.Keys;
import edu.utexas.tacc.tapis.systems.gen.jooq.TapisSys;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.records.CapabilitiesRecord;
import edu.utexas.tacc.tapis.systems.model.Capability.Category;
import edu.utexas.tacc.tapis.systems.model.Capability.Datatype;

import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row7;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Capabilities extends TableImpl<CapabilitiesRecord> {

    private static final long serialVersionUID = 467962194;

    /**
     * The reference instance of <code>tapis_sys.capabilities</code>
     */
    public static final Capabilities CAPABILITIES = new Capabilities();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<CapabilitiesRecord> getRecordType() {
        return CapabilitiesRecord.class;
    }

    /**
     * The column <code>tapis_sys.capabilities.seq_id</code>. Capability sequence id
     */
    public final TableField<CapabilitiesRecord, Integer> SEQ_ID = createField(DSL.name("seq_id"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("nextval('capabilities_seq_id_seq'::regclass)", org.jooq.impl.SQLDataType.INTEGER)), this, "Capability sequence id");

    /**
     * The column <code>tapis_sys.capabilities.system_seq_id</code>. Sequence id of system supporting the capability
     */
    public final TableField<CapabilitiesRecord, Integer> SYSTEM_SEQ_ID = createField(DSL.name("system_seq_id"), org.jooq.impl.SQLDataType.INTEGER, this, "Sequence id of system supporting the capability");

    /**
     * The column <code>tapis_sys.capabilities.category</code>. Category for grouping of capabilities
     */
    public final TableField<CapabilitiesRecord, Category> CATEGORY = createField(DSL.name("category"), org.jooq.impl.SQLDataType.VARCHAR.nullable(false).asEnumDataType(edu.utexas.tacc.tapis.systems.gen.jooq.enums.CapabilityCategoryType.class), this, "Category for grouping of capabilities", new org.jooq.impl.EnumConverter<edu.utexas.tacc.tapis.systems.gen.jooq.enums.CapabilityCategoryType, edu.utexas.tacc.tapis.systems.model.Capability.Category>(edu.utexas.tacc.tapis.systems.gen.jooq.enums.CapabilityCategoryType.class, edu.utexas.tacc.tapis.systems.model.Capability.Category.class));

    /**
     * The column <code>tapis_sys.capabilities.name</code>. Name of capability
     */
    public final TableField<CapabilitiesRecord, String> NAME = createField(DSL.name("name"), org.jooq.impl.SQLDataType.CLOB.nullable(false).defaultValue(org.jooq.impl.DSL.field("''::text", org.jooq.impl.SQLDataType.CLOB)), this, "Name of capability");

    /**
     * The column <code>tapis_sys.capabilities.datatype</code>. Datatype associated with the value
     */
    public final TableField<CapabilitiesRecord, Datatype> DATATYPE = createField(DSL.name("datatype"), org.jooq.impl.SQLDataType.VARCHAR.nullable(false).asEnumDataType(edu.utexas.tacc.tapis.systems.gen.jooq.enums.CapabilityDatatypeType.class), this, "Datatype associated with the value", new org.jooq.impl.EnumConverter<edu.utexas.tacc.tapis.systems.gen.jooq.enums.CapabilityDatatypeType, edu.utexas.tacc.tapis.systems.model.Capability.Datatype>(edu.utexas.tacc.tapis.systems.gen.jooq.enums.CapabilityDatatypeType.class, edu.utexas.tacc.tapis.systems.model.Capability.Datatype.class));

    /**
     * The column <code>tapis_sys.capabilities.precedence</code>. Precedence where higher number has higher precedence
     */
    public final TableField<CapabilitiesRecord, Integer> PRECEDENCE = createField(DSL.name("precedence"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("100", org.jooq.impl.SQLDataType.INTEGER)), this, "Precedence where higher number has higher precedence");

    /**
     * The column <code>tapis_sys.capabilities.value</code>. Value for the capability
     */
    public final TableField<CapabilitiesRecord, String> VALUE = createField(DSL.name("value"), org.jooq.impl.SQLDataType.CLOB.nullable(false).defaultValue(org.jooq.impl.DSL.field("''::text", org.jooq.impl.SQLDataType.CLOB)), this, "Value for the capability");

    /**
     * Create a <code>tapis_sys.capabilities</code> table reference
     */
    public Capabilities() {
        this(DSL.name("capabilities"), null);
    }

    /**
     * Create an aliased <code>tapis_sys.capabilities</code> table reference
     */
    public Capabilities(String alias) {
        this(DSL.name(alias), CAPABILITIES);
    }

    /**
     * Create an aliased <code>tapis_sys.capabilities</code> table reference
     */
    public Capabilities(Name alias) {
        this(alias, CAPABILITIES);
    }

    private Capabilities(Name alias, Table<CapabilitiesRecord> aliased) {
        this(alias, aliased, null);
    }

    private Capabilities(Name alias, Table<CapabilitiesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    public <O extends Record> Capabilities(Table<O> child, ForeignKey<O, CapabilitiesRecord> key) {
        super(child, key, CAPABILITIES);
    }

    @Override
    public Schema getSchema() {
        return TapisSys.TAPIS_SYS;
    }

    @Override
    public Identity<CapabilitiesRecord, Integer> getIdentity() {
        return Keys.IDENTITY_CAPABILITIES;
    }

    @Override
    public UniqueKey<CapabilitiesRecord> getPrimaryKey() {
        return Keys.CAPABILITIES_PKEY;
    }

    @Override
    public List<UniqueKey<CapabilitiesRecord>> getKeys() {
        return Arrays.<UniqueKey<CapabilitiesRecord>>asList(Keys.CAPABILITIES_PKEY, Keys.CAPABILITIES_SYSTEM_SEQ_ID_CATEGORY_NAME_KEY);
    }

    @Override
    public List<ForeignKey<CapabilitiesRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<CapabilitiesRecord, ?>>asList(Keys.CAPABILITIES__CAPABILITIES_SYSTEM_SEQ_ID_FKEY);
    }

    public Systems systems() {
        return new Systems(this, Keys.CAPABILITIES__CAPABILITIES_SYSTEM_SEQ_ID_FKEY);
    }

    @Override
    public Capabilities as(String alias) {
        return new Capabilities(DSL.name(alias), this);
    }

    @Override
    public Capabilities as(Name alias) {
        return new Capabilities(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Capabilities rename(String name) {
        return new Capabilities(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Capabilities rename(Name name) {
        return new Capabilities(name, null);
    }

    // -------------------------------------------------------------------------
    // Row7 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row7<Integer, Integer, Category, String, Datatype, Integer, String> fieldsRow() {
        return (Row7) super.fieldsRow();
    }
}
