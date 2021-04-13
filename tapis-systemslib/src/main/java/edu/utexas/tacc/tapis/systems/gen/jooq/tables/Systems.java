/*
 * This file is generated by jOOQ.
 */
package edu.utexas.tacc.tapis.systems.gen.jooq.tables;


import com.google.gson.JsonElement;

import edu.utexas.tacc.tapis.systems.dao.JSONBToJsonElementBinding;
import edu.utexas.tacc.tapis.systems.gen.jooq.Indexes;
import edu.utexas.tacc.tapis.systems.gen.jooq.Keys;
import edu.utexas.tacc.tapis.systems.gen.jooq.TapisSys;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.records.SystemsRecord;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.SchedulerType;
import edu.utexas.tacc.tapis.systems.model.TSystem.SystemType;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
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
public class Systems extends TableImpl<SystemsRecord> {

    private static final long serialVersionUID = 143273442;

    /**
     * The reference instance of <code>tapis_sys.systems</code>
     */
    public static final Systems SYSTEMS = new Systems();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SystemsRecord> getRecordType() {
        return SystemsRecord.class;
    }

    /**
     * The column <code>tapis_sys.systems.seq_id</code>. System sequence id
     */
    public final TableField<SystemsRecord, Integer> SEQ_ID = createField(DSL.name("seq_id"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("nextval('systems_seq_id_seq'::regclass)", org.jooq.impl.SQLDataType.INTEGER)), this, "System sequence id");

    /**
     * The column <code>tapis_sys.systems.tenant</code>. Tenant name associated with system
     */
    public final TableField<SystemsRecord, String> TENANT = createField(DSL.name("tenant"), org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "Tenant name associated with system");

    /**
     * The column <code>tapis_sys.systems.id</code>. Unique name for the system
     */
    public final TableField<SystemsRecord, String> ID = createField(DSL.name("id"), org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "Unique name for the system");

    /**
     * The column <code>tapis_sys.systems.description</code>. System description
     */
    public final TableField<SystemsRecord, String> DESCRIPTION = createField(DSL.name("description"), org.jooq.impl.SQLDataType.CLOB, this, "System description");

    /**
     * The column <code>tapis_sys.systems.system_type</code>. Type of system
     */
    public final TableField<SystemsRecord, SystemType> SYSTEM_TYPE = createField(DSL.name("system_type"), org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "Type of system", new org.jooq.impl.EnumConverter<java.lang.String, edu.utexas.tacc.tapis.systems.model.TSystem.SystemType>(java.lang.String.class, edu.utexas.tacc.tapis.systems.model.TSystem.SystemType.class));

    /**
     * The column <code>tapis_sys.systems.owner</code>. User name of system owner
     */
    public final TableField<SystemsRecord, String> OWNER = createField(DSL.name("owner"), org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "User name of system owner");

    /**
     * The column <code>tapis_sys.systems.host</code>. System host name or ip address
     */
    public final TableField<SystemsRecord, String> HOST = createField(DSL.name("host"), org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "System host name or ip address");

    /**
     * The column <code>tapis_sys.systems.enabled</code>. Indicates if system is currently active and available for use
     */
    public final TableField<SystemsRecord, Boolean> ENABLED = createField(DSL.name("enabled"), org.jooq.impl.SQLDataType.BOOLEAN.nullable(false).defaultValue(org.jooq.impl.DSL.field("true", org.jooq.impl.SQLDataType.BOOLEAN)), this, "Indicates if system is currently active and available for use");

    /**
     * The column <code>tapis_sys.systems.effective_user_id</code>. User name to use when accessing the system
     */
    public final TableField<SystemsRecord, String> EFFECTIVE_USER_ID = createField(DSL.name("effective_user_id"), org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "User name to use when accessing the system");

    /**
     * The column <code>tapis_sys.systems.default_authn_method</code>. Enum for how authorization is handled by default
     */
    public final TableField<SystemsRecord, AuthnMethod> DEFAULT_AUTHN_METHOD = createField(DSL.name("default_authn_method"), org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "Enum for how authorization is handled by default", new org.jooq.impl.EnumConverter<java.lang.String, edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod>(java.lang.String.class, edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod.class));

    /**
     * The column <code>tapis_sys.systems.bucket_name</code>. Name of the bucket for an S3 system
     */
    public final TableField<SystemsRecord, String> BUCKET_NAME = createField(DSL.name("bucket_name"), org.jooq.impl.SQLDataType.CLOB, this, "Name of the bucket for an S3 system");

    /**
     * The column <code>tapis_sys.systems.root_dir</code>. Effective root directory path for a Unix system
     */
    public final TableField<SystemsRecord, String> ROOT_DIR = createField(DSL.name("root_dir"), org.jooq.impl.SQLDataType.CLOB, this, "Effective root directory path for a Unix system");

    /**
     * The column <code>tapis_sys.systems.transfer_methods</code>. List of supported transfer methods
     */
    public final TableField<SystemsRecord, String[]> TRANSFER_METHODS = createField(DSL.name("transfer_methods"), org.jooq.impl.SQLDataType.CLOB.getArrayDataType(), this, "List of supported transfer methods");

    /**
     * The column <code>tapis_sys.systems.port</code>. Port number used to access a system
     */
    public final TableField<SystemsRecord, Integer> PORT = createField(DSL.name("port"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Port number used to access a system");

    /**
     * The column <code>tapis_sys.systems.use_proxy</code>. Indicates if system should accessed through a proxy
     */
    public final TableField<SystemsRecord, Boolean> USE_PROXY = createField(DSL.name("use_proxy"), org.jooq.impl.SQLDataType.BOOLEAN.nullable(false).defaultValue(org.jooq.impl.DSL.field("false", org.jooq.impl.SQLDataType.BOOLEAN)), this, "Indicates if system should accessed through a proxy");

    /**
     * The column <code>tapis_sys.systems.proxy_host</code>. Proxy host name or ip address
     */
    public final TableField<SystemsRecord, String> PROXY_HOST = createField(DSL.name("proxy_host"), org.jooq.impl.SQLDataType.CLOB.nullable(false).defaultValue(org.jooq.impl.DSL.field("''::text", org.jooq.impl.SQLDataType.CLOB)), this, "Proxy host name or ip address");

    /**
     * The column <code>tapis_sys.systems.proxy_port</code>. Proxy port number
     */
    public final TableField<SystemsRecord, Integer> PROXY_PORT = createField(DSL.name("proxy_port"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Proxy port number");

    /**
     * The column <code>tapis_sys.systems.dtn_system_id</code>. Alternate system to use as a Data Transfer Node (DTN)
     */
    public final TableField<SystemsRecord, String> DTN_SYSTEM_ID = createField(DSL.name("dtn_system_id"), org.jooq.impl.SQLDataType.CLOB, this, "Alternate system to use as a Data Transfer Node (DTN)");

    /**
     * The column <code>tapis_sys.systems.dtn_mount_point</code>. Mount point on local system for the DTN
     */
    public final TableField<SystemsRecord, String> DTN_MOUNT_POINT = createField(DSL.name("dtn_mount_point"), org.jooq.impl.SQLDataType.CLOB, this, "Mount point on local system for the DTN");

    /**
     * The column <code>tapis_sys.systems.dtn_mount_source_path</code>.
     */
    public final TableField<SystemsRecord, String> DTN_MOUNT_SOURCE_PATH = createField(DSL.name("dtn_mount_source_path"), org.jooq.impl.SQLDataType.CLOB, this, "");

    /**
     * The column <code>tapis_sys.systems.is_dtn</code>. Indicates if system is to serve as a data transfer node
     */
    public final TableField<SystemsRecord, Boolean> IS_DTN = createField(DSL.name("is_dtn"), org.jooq.impl.SQLDataType.BOOLEAN.nullable(false).defaultValue(org.jooq.impl.DSL.field("false", org.jooq.impl.SQLDataType.BOOLEAN)), this, "Indicates if system is to serve as a data transfer node");

    /**
     * The column <code>tapis_sys.systems.can_exec</code>. Indicates if system can be used to execute jobs
     */
    public final TableField<SystemsRecord, Boolean> CAN_EXEC = createField(DSL.name("can_exec"), org.jooq.impl.SQLDataType.BOOLEAN.nullable(false).defaultValue(org.jooq.impl.DSL.field("false", org.jooq.impl.SQLDataType.BOOLEAN)), this, "Indicates if system can be used to execute jobs");

    /**
     * The column <code>tapis_sys.systems.job_working_dir</code>. Parent directory from which a job is run. Relative to effective root directory.
     */
    public final TableField<SystemsRecord, String> JOB_WORKING_DIR = createField(DSL.name("job_working_dir"), org.jooq.impl.SQLDataType.CLOB, this, "Parent directory from which a job is run. Relative to effective root directory.");

    /**
     * The column <code>tapis_sys.systems.job_env_variables</code>. Environment variables added to shell environment
     */
    public final TableField<SystemsRecord, String[]> JOB_ENV_VARIABLES = createField(DSL.name("job_env_variables"), org.jooq.impl.SQLDataType.CLOB.getArrayDataType(), this, "Environment variables added to shell environment");

    /**
     * The column <code>tapis_sys.systems.job_max_jobs</code>. Maximum total number of jobs that can be queued or running on the system at a given time.
     */
    public final TableField<SystemsRecord, Integer> JOB_MAX_JOBS = createField(DSL.name("job_max_jobs"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Maximum total number of jobs that can be queued or running on the system at a given time.");

    /**
     * The column <code>tapis_sys.systems.job_max_jobs_per_user</code>. Maximum total number of jobs associated with a specific user that can be queued or running on the system at a given time.
     */
    public final TableField<SystemsRecord, Integer> JOB_MAX_JOBS_PER_USER = createField(DSL.name("job_max_jobs_per_user"), org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("'-1'::integer", org.jooq.impl.SQLDataType.INTEGER)), this, "Maximum total number of jobs associated with a specific user that can be queued or running on the system at a given time.");

    /**
     * The column <code>tapis_sys.systems.job_is_batch</code>. Flag indicating if system uses a batch scheduler to run jobs.
     */
    public final TableField<SystemsRecord, Boolean> JOB_IS_BATCH = createField(DSL.name("job_is_batch"), org.jooq.impl.SQLDataType.BOOLEAN.nullable(false).defaultValue(org.jooq.impl.DSL.field("false", org.jooq.impl.SQLDataType.BOOLEAN)), this, "Flag indicating if system uses a batch scheduler to run jobs.");

    /**
     * The column <code>tapis_sys.systems.batch_scheduler</code>. Type of scheduler used when running batch jobs
     */
    public final TableField<SystemsRecord, SchedulerType> BATCH_SCHEDULER = createField(DSL.name("batch_scheduler"), org.jooq.impl.SQLDataType.CLOB, this, "Type of scheduler used when running batch jobs", new org.jooq.impl.EnumConverter<java.lang.String, edu.utexas.tacc.tapis.systems.model.TSystem.SchedulerType>(java.lang.String.class, edu.utexas.tacc.tapis.systems.model.TSystem.SchedulerType.class));

    /**
     * The column <code>tapis_sys.systems.batch_default_logical_queue</code>. Default logical batch queue for the system
     */
    public final TableField<SystemsRecord, String> BATCH_DEFAULT_LOGICAL_QUEUE = createField(DSL.name("batch_default_logical_queue"), org.jooq.impl.SQLDataType.CLOB, this, "Default logical batch queue for the system");

    /**
     * The column <code>tapis_sys.systems.tags</code>. Tags for user supplied key:value pairs
     */
    public final TableField<SystemsRecord, String[]> TAGS = createField(DSL.name("tags"), org.jooq.impl.SQLDataType.CLOB.getArrayDataType(), this, "Tags for user supplied key:value pairs");

    /**
     * The column <code>tapis_sys.systems.notes</code>. Notes for general information stored as JSON
     */
    public final TableField<SystemsRecord, JsonElement> NOTES = createField(DSL.name("notes"), org.jooq.impl.SQLDataType.JSONB.nullable(false), this, "Notes for general information stored as JSON", new JSONBToJsonElementBinding());

    /**
     * The column <code>tapis_sys.systems.uuid</code>.
     */
    public final TableField<SystemsRecord, UUID> UUID = createField(DSL.name("uuid"), org.jooq.impl.SQLDataType.UUID.nullable(false), this, "");

    /**
     * The column <code>tapis_sys.systems.deleted</code>. Indicates if system has been soft deleted
     */
    public final TableField<SystemsRecord, Boolean> DELETED = createField(DSL.name("deleted"), org.jooq.impl.SQLDataType.BOOLEAN.nullable(false).defaultValue(org.jooq.impl.DSL.field("false", org.jooq.impl.SQLDataType.BOOLEAN)), this, "Indicates if system has been soft deleted");

    /**
     * The column <code>tapis_sys.systems.created</code>. UTC time for when record was created
     */
    public final TableField<SystemsRecord, LocalDateTime> CREATED = createField(DSL.name("created"), org.jooq.impl.SQLDataType.LOCALDATETIME.nullable(false).defaultValue(org.jooq.impl.DSL.field("timezone('utc'::text, now())", org.jooq.impl.SQLDataType.LOCALDATETIME)), this, "UTC time for when record was created");

    /**
     * The column <code>tapis_sys.systems.updated</code>. UTC time for when record was last updated
     */
    public final TableField<SystemsRecord, LocalDateTime> UPDATED = createField(DSL.name("updated"), org.jooq.impl.SQLDataType.LOCALDATETIME.nullable(false).defaultValue(org.jooq.impl.DSL.field("timezone('utc'::text, now())", org.jooq.impl.SQLDataType.LOCALDATETIME)), this, "UTC time for when record was last updated");

    /**
     * Create a <code>tapis_sys.systems</code> table reference
     */
    public Systems() {
        this(DSL.name("systems"), null);
    }

    /**
     * Create an aliased <code>tapis_sys.systems</code> table reference
     */
    public Systems(String alias) {
        this(DSL.name(alias), SYSTEMS);
    }

    /**
     * Create an aliased <code>tapis_sys.systems</code> table reference
     */
    public Systems(Name alias) {
        this(alias, SYSTEMS);
    }

    private Systems(Name alias, Table<SystemsRecord> aliased) {
        this(alias, aliased, null);
    }

    private Systems(Name alias, Table<SystemsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    public <O extends Record> Systems(Table<O> child, ForeignKey<O, SystemsRecord> key) {
        super(child, key, SYSTEMS);
    }

    @Override
    public Schema getSchema() {
        return TapisSys.TAPIS_SYS;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.SYS_HOST_IDX, Indexes.SYS_OWNER_IDX, Indexes.SYS_TAGS_IDX, Indexes.SYS_TENANT_NAME_IDX);
    }

    @Override
    public Identity<SystemsRecord, Integer> getIdentity() {
        return Keys.IDENTITY_SYSTEMS;
    }

    @Override
    public UniqueKey<SystemsRecord> getPrimaryKey() {
        return Keys.SYSTEMS_PKEY;
    }

    @Override
    public List<UniqueKey<SystemsRecord>> getKeys() {
        return Arrays.<UniqueKey<SystemsRecord>>asList(Keys.SYSTEMS_PKEY, Keys.SYSTEMS_TENANT_ID_KEY);
    }

    @Override
    public Systems as(String alias) {
        return new Systems(DSL.name(alias), this);
    }

    @Override
    public Systems as(Name alias) {
        return new Systems(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Systems rename(String name) {
        return new Systems(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Systems rename(Name name) {
        return new Systems(name, null);
    }
}
