/*
 * This file is generated by jOOQ.
 */
package edu.utexas.tacc.tapis.systems.gen.jooq.tables.records;


import edu.utexas.tacc.tapis.systems.gen.jooq.tables.Capabilities;
import edu.utexas.tacc.tapis.systems.model.Capability.Category;
import edu.utexas.tacc.tapis.systems.model.Capability.Datatype;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record7;
import org.jooq.Row7;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class CapabilitiesRecord extends UpdatableRecordImpl<CapabilitiesRecord> implements Record7<Integer, Integer, Category, String, Datatype, Integer, String> {

    private static final long serialVersionUID = -1893687006;

    /**
     * Setter for <code>tapis_sys.capabilities.seq_id</code>. Capability sequence id
     */
    public void setSeqId(Integer value) {
        set(0, value);
    }

    /**
     * Getter for <code>tapis_sys.capabilities.seq_id</code>. Capability sequence id
     */
    public Integer getSeqId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>tapis_sys.capabilities.system_seq_id</code>. Sequenc id of system supporting the capability
     */
    public void setSystemSeqId(Integer value) {
        set(1, value);
    }

    /**
     * Getter for <code>tapis_sys.capabilities.system_seq_id</code>. Sequenc id of system supporting the capability
     */
    public Integer getSystemSeqId() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>tapis_sys.capabilities.category</code>. Category for grouping of capabilities
     */
    public void setCategory(Category value) {
        set(2, value);
    }

    /**
     * Getter for <code>tapis_sys.capabilities.category</code>. Category for grouping of capabilities
     */
    public Category getCategory() {
        return (Category) get(2);
    }

    /**
     * Setter for <code>tapis_sys.capabilities.name</code>. Name of capability
     */
    public void setName(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>tapis_sys.capabilities.name</code>. Name of capability
     */
    public String getName() {
        return (String) get(3);
    }

    /**
     * Setter for <code>tapis_sys.capabilities.datatype</code>. Datatype associated with the value
     */
    public void setDatatype(Datatype value) {
        set(4, value);
    }

    /**
     * Getter for <code>tapis_sys.capabilities.datatype</code>. Datatype associated with the value
     */
    public Datatype getDatatype() {
        return (Datatype) get(4);
    }

    /**
     * Setter for <code>tapis_sys.capabilities.precedence</code>. Precedence where higher number has higher precedence
     */
    public void setPrecedence(Integer value) {
        set(5, value);
    }

    /**
     * Getter for <code>tapis_sys.capabilities.precedence</code>. Precedence where higher number has higher precedence
     */
    public Integer getPrecedence() {
        return (Integer) get(5);
    }

    /**
     * Setter for <code>tapis_sys.capabilities.value</code>. Value for the capability
     */
    public void setValue(String value) {
        set(6, value);
    }

    /**
     * Getter for <code>tapis_sys.capabilities.value</code>. Value for the capability
     */
    public String getValue() {
        return (String) get(6);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record7 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row7<Integer, Integer, Category, String, Datatype, Integer, String> fieldsRow() {
        return (Row7) super.fieldsRow();
    }

    @Override
    public Row7<Integer, Integer, Category, String, Datatype, Integer, String> valuesRow() {
        return (Row7) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return Capabilities.CAPABILITIES.SEQ_ID;
    }

    @Override
    public Field<Integer> field2() {
        return Capabilities.CAPABILITIES.SYSTEM_SEQ_ID;
    }

    @Override
    public Field<Category> field3() {
        return Capabilities.CAPABILITIES.CATEGORY;
    }

    @Override
    public Field<String> field4() {
        return Capabilities.CAPABILITIES.NAME;
    }

    @Override
    public Field<Datatype> field5() {
        return Capabilities.CAPABILITIES.DATATYPE;
    }

    @Override
    public Field<Integer> field6() {
        return Capabilities.CAPABILITIES.PRECEDENCE;
    }

    @Override
    public Field<String> field7() {
        return Capabilities.CAPABILITIES.VALUE;
    }

    @Override
    public Integer component1() {
        return getSeqId();
    }

    @Override
    public Integer component2() {
        return getSystemSeqId();
    }

    @Override
    public Category component3() {
        return getCategory();
    }

    @Override
    public String component4() {
        return getName();
    }

    @Override
    public Datatype component5() {
        return getDatatype();
    }

    @Override
    public Integer component6() {
        return getPrecedence();
    }

    @Override
    public String component7() {
        return getValue();
    }

    @Override
    public Integer value1() {
        return getSeqId();
    }

    @Override
    public Integer value2() {
        return getSystemSeqId();
    }

    @Override
    public Category value3() {
        return getCategory();
    }

    @Override
    public String value4() {
        return getName();
    }

    @Override
    public Datatype value5() {
        return getDatatype();
    }

    @Override
    public Integer value6() {
        return getPrecedence();
    }

    @Override
    public String value7() {
        return getValue();
    }

    @Override
    public CapabilitiesRecord value1(Integer value) {
        setSeqId(value);
        return this;
    }

    @Override
    public CapabilitiesRecord value2(Integer value) {
        setSystemSeqId(value);
        return this;
    }

    @Override
    public CapabilitiesRecord value3(Category value) {
        setCategory(value);
        return this;
    }

    @Override
    public CapabilitiesRecord value4(String value) {
        setName(value);
        return this;
    }

    @Override
    public CapabilitiesRecord value5(Datatype value) {
        setDatatype(value);
        return this;
    }

    @Override
    public CapabilitiesRecord value6(Integer value) {
        setPrecedence(value);
        return this;
    }

    @Override
    public CapabilitiesRecord value7(String value) {
        setValue(value);
        return this;
    }

    @Override
    public CapabilitiesRecord values(Integer value1, Integer value2, Category value3, String value4, Datatype value5, Integer value6, String value7) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached CapabilitiesRecord
     */
    public CapabilitiesRecord() {
        super(Capabilities.CAPABILITIES);
    }

    /**
     * Create a detached, initialised CapabilitiesRecord
     */
    public CapabilitiesRecord(Integer seqId, Integer systemSeqId, Category category, String name, Datatype datatype, Integer precedence, String value) {
        super(Capabilities.CAPABILITIES);

        set(0, seqId);
        set(1, systemSeqId);
        set(2, category);
        set(3, name);
        set(4, datatype);
        set(5, precedence);
        set(6, value);
    }
}
