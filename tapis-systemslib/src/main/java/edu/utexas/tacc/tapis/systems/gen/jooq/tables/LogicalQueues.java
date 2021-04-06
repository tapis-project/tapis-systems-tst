/*
 * This file is generated by jOOQ.
 */
package edu.utexas.tacc.tapis.systems.gen.jooq.tables;


import edu.utexas.tacc.tapis.systems.gen.jooq.Keys;
import edu.utexas.tacc.tapis.systems.gen.jooq.TapisSys;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.records.LogicalQueuesRecord;

import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row10;
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
public class LogicalQueues extends TableImpl<LogicalQueuesRecord> {

    private static final long serialVersionUID = 1500958841;

    /**
     * The reference instance of <code>tapis_sys.logical_queues</code>
     */
    public static final LogicalQueues LOGICAL_QUEUES = new LogicalQueues();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<LogicalQueuesRecord> getRecordType() {
        return LogicalQueuesRecord.class;
    }

    /**
     * The column <code>tapis_sys.logical_queues.seq_id</code>. Logical queue sequence id
     */
    public final TableField<LogicalQueuesRecord, Integer> SEQ_ID = createField(DSL.name("seq_id"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("nextval('logical_queues_seq_id_seq'::regclass)", org.jooq.impl.SQLDataType.INTEGER)), this, "Logical queue sequence id");

    /**
     * The column <code>tapis_sys.logical_queues.system_seq_id</code>. Sequence id of system associated with the logical queue
     */
    public final TableField<LogicalQueuesRecord, Integer> SYSTEM_SEQ_ID = createField(DSL.name("system_seq_id"), org.jooq.impl.SQLDataType.INTEGER, this, "Sequence id of system associated with the logical queue");

    /**
     * The column <code>tapis_sys.logical_queues.name</code>. Name of logical queue
     */
    public final TableField<LogicalQueuesRecord, String> NAME = createField(DSL.name("name"), org.jooq.impl.SQLDataType.CLOB.nullable(false).defaultValue(org.jooq.impl.DSL.field("''::text", org.jooq.impl.SQLDataType.CLOB)), this, "Name of logical queue");

    /**
     * The column <code>tapis_sys.logical_queues.hpc_queue_name</code>. Name of the associated hpc queue
     */
    public final TableField<LogicalQueuesRecord, String> HPC_QUEUE_NAME = createField(DSL.name("hpc_queue_name"), org.jooq.impl.SQLDataType.CLOB.nullable(false).defaultValue(org.jooq.impl.DSL.field("''::text", org.jooq.impl.SQLDataType.CLOB)), this, "Name of the associated hpc queue");

    /**
     * The column <code>tapis_sys.logical_queues.max_jobs</code>. Maximum total number of jobs that can be queued or running in this queue at a given time.
     */
    public final TableField<LogicalQueuesRecord, Integer> MAX_JOBS = createField(DSL.name("max_jobs"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Maximum total number of jobs that can be queued or running in this queue at a given time.");

    /**
     * The column <code>tapis_sys.logical_queues.max_jobs_per_user</code>. Maximum number of jobs associated with a specific user that can be queued or running in this queue at a given time.
     */
    public final TableField<LogicalQueuesRecord, Integer> MAX_JOBS_PER_USER = createField(DSL.name("max_jobs_per_user"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Maximum number of jobs associated with a specific user that can be queued or running in this queue at a given time.");

    /**
     * The column <code>tapis_sys.logical_queues.max_node_count</code>. Maximum number of nodes that can be requested when submitting a job to the queue.
     */
    public final TableField<LogicalQueuesRecord, Integer> MAX_NODE_COUNT = createField(DSL.name("max_node_count"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Maximum number of nodes that can be requested when submitting a job to the queue.");

    /**
     * The column <code>tapis_sys.logical_queues.max_cores_per_node</code>. Maximum number of cores per node that can be requested when submitting a job to the queue.
     */
    public final TableField<LogicalQueuesRecord, Integer> MAX_CORES_PER_NODE = createField(DSL.name("max_cores_per_node"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Maximum number of cores per node that can be requested when submitting a job to the queue.");

    /**
     * The column <code>tapis_sys.logical_queues.max_memory_mb</code>. Maximum memory in megabytes that can be requested when submitting a job to the queue.
     */
    public final TableField<LogicalQueuesRecord, Integer> MAX_MEMORY_MB = createField(DSL.name("max_memory_mb"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Maximum memory in megabytes that can be requested when submitting a job to the queue.");

    /**
     * The column <code>tapis_sys.logical_queues.max_minutes</code>. Maximum run time in minutes that can be requested when submitting a job to the queue.
     */
    public final TableField<LogicalQueuesRecord, Integer> MAX_MINUTES = createField(DSL.name("max_minutes"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Maximum run time in minutes that can be requested when submitting a job to the queue.");

    /**
     * Create a <code>tapis_sys.logical_queues</code> table reference
     */
    public LogicalQueues() {
        this(DSL.name("logical_queues"), null);
    }

    /**
     * Create an aliased <code>tapis_sys.logical_queues</code> table reference
     */
    public LogicalQueues(String alias) {
        this(DSL.name(alias), LOGICAL_QUEUES);
    }

    /**
     * Create an aliased <code>tapis_sys.logical_queues</code> table reference
     */
    public LogicalQueues(Name alias) {
        this(alias, LOGICAL_QUEUES);
    }

    private LogicalQueues(Name alias, Table<LogicalQueuesRecord> aliased) {
        this(alias, aliased, null);
    }

    private LogicalQueues(Name alias, Table<LogicalQueuesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    public <O extends Record> LogicalQueues(Table<O> child, ForeignKey<O, LogicalQueuesRecord> key) {
        super(child, key, LOGICAL_QUEUES);
    }

    @Override
    public Schema getSchema() {
        return TapisSys.TAPIS_SYS;
    }

    @Override
    public Identity<LogicalQueuesRecord, Integer> getIdentity() {
        return Keys.IDENTITY_LOGICAL_QUEUES;
    }

    @Override
    public UniqueKey<LogicalQueuesRecord> getPrimaryKey() {
        return Keys.LOGICAL_QUEUES_PKEY;
    }

    @Override
    public List<UniqueKey<LogicalQueuesRecord>> getKeys() {
        return Arrays.<UniqueKey<LogicalQueuesRecord>>asList(Keys.LOGICAL_QUEUES_PKEY, Keys.LOGICAL_QUEUES_SYSTEM_SEQ_ID_NAME_KEY);
    }

    @Override
    public List<ForeignKey<LogicalQueuesRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<LogicalQueuesRecord, ?>>asList(Keys.LOGICAL_QUEUES__LOGICAL_QUEUES_SYSTEM_SEQ_ID_FKEY);
    }

    public Systems systems() {
        return new Systems(this, Keys.LOGICAL_QUEUES__LOGICAL_QUEUES_SYSTEM_SEQ_ID_FKEY);
    }

    @Override
    public LogicalQueues as(String alias) {
        return new LogicalQueues(DSL.name(alias), this);
    }

    @Override
    public LogicalQueues as(Name alias) {
        return new LogicalQueues(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public LogicalQueues rename(String name) {
        return new LogicalQueues(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public LogicalQueues rename(Name name) {
        return new LogicalQueues(name, null);
    }

    // -------------------------------------------------------------------------
    // Row10 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row10<Integer, Integer, String, String, Integer, Integer, Integer, Integer, Integer, Integer> fieldsRow() {
        return (Row10) super.fieldsRow();
    }
}
