/*
 * This file is generated by jOOQ.
 */
package edu.utexas.tacc.tapis.systems.gen.jooq;


import edu.utexas.tacc.tapis.systems.gen.jooq.tables.Capabilities;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.FlywaySchemaHistory;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.JobRuntimes;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.LogicalQueues;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.SystemUpdates;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.Systems;

import java.util.Arrays;
import java.util.List;

import org.jooq.Catalog;
import org.jooq.Sequence;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class TapisSys extends SchemaImpl {

    private static final long serialVersionUID = -1764432209;

    /**
     * The reference instance of <code>tapis_sys</code>
     */
    public static final TapisSys TAPIS_SYS = new TapisSys();

    /**
     * The table <code>tapis_sys.capabilities</code>.
     */
    public final Capabilities CAPABILITIES = Capabilities.CAPABILITIES;

    /**
     * The table <code>tapis_sys.flyway_schema_history</code>.
     */
    public final FlywaySchemaHistory FLYWAY_SCHEMA_HISTORY = FlywaySchemaHistory.FLYWAY_SCHEMA_HISTORY;

    /**
     * The table <code>tapis_sys.job_runtimes</code>.
     */
    public final JobRuntimes JOB_RUNTIMES = JobRuntimes.JOB_RUNTIMES;

    /**
     * The table <code>tapis_sys.logical_queues</code>.
     */
    public final LogicalQueues LOGICAL_QUEUES = LogicalQueues.LOGICAL_QUEUES;

    /**
     * The table <code>tapis_sys.system_updates</code>.
     */
    public final SystemUpdates SYSTEM_UPDATES = SystemUpdates.SYSTEM_UPDATES;

    /**
     * The table <code>tapis_sys.systems</code>.
     */
    public final Systems SYSTEMS = Systems.SYSTEMS;

    /**
     * No further instances allowed
     */
    private TapisSys() {
        super("tapis_sys", null);
    }


    @Override
    public Catalog getCatalog() {
        return DefaultCatalog.DEFAULT_CATALOG;
    }

    @Override
    public final List<Sequence<?>> getSequences() {
        return Arrays.<Sequence<?>>asList(
            Sequences.CAPABILITIES_SEQ_ID_SEQ,
            Sequences.JOB_RUNTIMES_SEQ_ID_SEQ,
            Sequences.LOGICAL_QUEUES_SEQ_ID_SEQ,
            Sequences.SYSTEM_UPDATES_SEQ_ID_SEQ,
            Sequences.SYSTEMS_SEQ_ID_SEQ);
    }

    @Override
    public final List<Table<?>> getTables() {
        return Arrays.<Table<?>>asList(
            Capabilities.CAPABILITIES,
            FlywaySchemaHistory.FLYWAY_SCHEMA_HISTORY,
            JobRuntimes.JOB_RUNTIMES,
            LogicalQueues.LOGICAL_QUEUES,
            SystemUpdates.SYSTEM_UPDATES,
            Systems.SYSTEMS);
    }
}
