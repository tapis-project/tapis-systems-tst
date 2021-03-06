/*
 * This file is generated by jOOQ.
 */
package edu.utexas.tacc.tapis.systems.gen.jooq.tables;


import com.google.gson.JsonElement;

import edu.utexas.tacc.tapis.systems.dao.JSONBToJsonElementBinding;
import edu.utexas.tacc.tapis.systems.gen.jooq.Keys;
import edu.utexas.tacc.tapis.systems.gen.jooq.TapisSys;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.records.SystemUpdatesRecord;
import edu.utexas.tacc.tapis.systems.model.TSystem.SystemOperation;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row11;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.EnumConverter;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class SystemUpdates extends TableImpl<SystemUpdatesRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>tapis_sys.system_updates</code>
     */
    public static final SystemUpdates SYSTEM_UPDATES = new SystemUpdates();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SystemUpdatesRecord> getRecordType() {
        return SystemUpdatesRecord.class;
    }

    /**
     * The column <code>tapis_sys.system_updates.seq_id</code>. System update request sequence id
     */
    public final TableField<SystemUpdatesRecord, Integer> SEQ_ID = createField(DSL.name("seq_id"), SQLDataType.INTEGER.nullable(false).identity(true), this, "System update request sequence id");

    /**
     * The column <code>tapis_sys.system_updates.system_seq_id</code>. Sequence id of system being updated
     */
    public final TableField<SystemUpdatesRecord, Integer> SYSTEM_SEQ_ID = createField(DSL.name("system_seq_id"), SQLDataType.INTEGER, this, "Sequence id of system being updated");

    /**
     * The column <code>tapis_sys.system_updates.system_tenant</code>. Tenant of system being updated
     */
    public final TableField<SystemUpdatesRecord, String> SYSTEM_TENANT = createField(DSL.name("system_tenant"), SQLDataType.CLOB.nullable(false), this, "Tenant of system being updated");

    /**
     * The column <code>tapis_sys.system_updates.system_id</code>. Id of system being updated
     */
    public final TableField<SystemUpdatesRecord, String> SYSTEM_ID = createField(DSL.name("system_id"), SQLDataType.CLOB.nullable(false), this, "Id of system being updated");

    /**
     * The column <code>tapis_sys.system_updates.user_tenant</code>. Tenant of user who requested the update
     */
    public final TableField<SystemUpdatesRecord, String> USER_TENANT = createField(DSL.name("user_tenant"), SQLDataType.CLOB.nullable(false), this, "Tenant of user who requested the update");

    /**
     * The column <code>tapis_sys.system_updates.user_name</code>. Name of user who requested the update
     */
    public final TableField<SystemUpdatesRecord, String> USER_NAME = createField(DSL.name("user_name"), SQLDataType.CLOB.nullable(false), this, "Name of user who requested the update");

    /**
     * The column <code>tapis_sys.system_updates.operation</code>. Type of update operation
     */
    public final TableField<SystemUpdatesRecord, SystemOperation> OPERATION = createField(DSL.name("operation"), SQLDataType.CLOB.nullable(false), this, "Type of update operation", new EnumConverter<String, SystemOperation>(String.class, SystemOperation.class));

    /**
     * The column <code>tapis_sys.system_updates.upd_json</code>. JSON representing the update - with secrets scrubbed
     */
    public final TableField<SystemUpdatesRecord, JsonElement> UPD_JSON = createField(DSL.name("upd_json"), SQLDataType.JSONB.nullable(false), this, "JSON representing the update - with secrets scrubbed", new JSONBToJsonElementBinding());

    /**
     * The column <code>tapis_sys.system_updates.upd_text</code>. Text data supplied by client - secrets should be scrubbed
     */
    public final TableField<SystemUpdatesRecord, String> UPD_TEXT = createField(DSL.name("upd_text"), SQLDataType.CLOB, this, "Text data supplied by client - secrets should be scrubbed");

    /**
     * The column <code>tapis_sys.system_updates.uuid</code>.
     */
    public final TableField<SystemUpdatesRecord, java.util.UUID> UUID = createField(DSL.name("uuid"), SQLDataType.UUID.nullable(false), this, "");

    /**
     * The column <code>tapis_sys.system_updates.created</code>. UTC time for when record was created
     */
    public final TableField<SystemUpdatesRecord, LocalDateTime> CREATED = createField(DSL.name("created"), SQLDataType.LOCALDATETIME(6).nullable(false).defaultValue(DSL.field("timezone('utc'::text, now())", SQLDataType.LOCALDATETIME)), this, "UTC time for when record was created");

    private SystemUpdates(Name alias, Table<SystemUpdatesRecord> aliased) {
        this(alias, aliased, null);
    }

    private SystemUpdates(Name alias, Table<SystemUpdatesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>tapis_sys.system_updates</code> table reference
     */
    public SystemUpdates(String alias) {
        this(DSL.name(alias), SYSTEM_UPDATES);
    }

    /**
     * Create an aliased <code>tapis_sys.system_updates</code> table reference
     */
    public SystemUpdates(Name alias) {
        this(alias, SYSTEM_UPDATES);
    }

    /**
     * Create a <code>tapis_sys.system_updates</code> table reference
     */
    public SystemUpdates() {
        this(DSL.name("system_updates"), null);
    }

    public <O extends Record> SystemUpdates(Table<O> child, ForeignKey<O, SystemUpdatesRecord> key) {
        super(child, key, SYSTEM_UPDATES);
    }

    @Override
    public Schema getSchema() {
        return TapisSys.TAPIS_SYS;
    }

    @Override
    public Identity<SystemUpdatesRecord, Integer> getIdentity() {
        return (Identity<SystemUpdatesRecord, Integer>) super.getIdentity();
    }

    @Override
    public UniqueKey<SystemUpdatesRecord> getPrimaryKey() {
        return Keys.SYSTEM_UPDATES_PKEY;
    }

    @Override
    public List<UniqueKey<SystemUpdatesRecord>> getKeys() {
        return Arrays.<UniqueKey<SystemUpdatesRecord>>asList(Keys.SYSTEM_UPDATES_PKEY);
    }

    @Override
    public List<ForeignKey<SystemUpdatesRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<SystemUpdatesRecord, ?>>asList(Keys.SYSTEM_UPDATES__SYSTEM_UPDATES_SYSTEM_SEQ_ID_FKEY);
    }

    private transient Systems _systems;

    public Systems systems() {
        if (_systems == null)
            _systems = new Systems(this, Keys.SYSTEM_UPDATES__SYSTEM_UPDATES_SYSTEM_SEQ_ID_FKEY);

        return _systems;
    }

    @Override
    public SystemUpdates as(String alias) {
        return new SystemUpdates(DSL.name(alias), this);
    }

    @Override
    public SystemUpdates as(Name alias) {
        return new SystemUpdates(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public SystemUpdates rename(String name) {
        return new SystemUpdates(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SystemUpdates rename(Name name) {
        return new SystemUpdates(name, null);
    }

    // -------------------------------------------------------------------------
    // Row11 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row11<Integer, Integer, String, String, String, String, SystemOperation, JsonElement, String, java.util.UUID, LocalDateTime> fieldsRow() {
        return (Row11) super.fieldsRow();
    }
}
