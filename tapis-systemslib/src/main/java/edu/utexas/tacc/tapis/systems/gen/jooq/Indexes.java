/*
 * This file is generated by jOOQ.
 */
package edu.utexas.tacc.tapis.systems.gen.jooq;


import edu.utexas.tacc.tapis.systems.gen.jooq.tables.FlywaySchemaHistory;
import edu.utexas.tacc.tapis.systems.gen.jooq.tables.Systems;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.Internal;


/**
 * A class modelling indexes of tables of the <code>tapis_sys</code> schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index FLYWAY_SCHEMA_HISTORY_S_IDX = Indexes0.FLYWAY_SCHEMA_HISTORY_S_IDX;
    public static final Index SYS_HOST_IDX = Indexes0.SYS_HOST_IDX;
    public static final Index SYS_OWNER_IDX = Indexes0.SYS_OWNER_IDX;
    public static final Index SYS_TAGS_IDX = Indexes0.SYS_TAGS_IDX;
    public static final Index SYS_TENANT_NAME_IDX = Indexes0.SYS_TENANT_NAME_IDX;

    // -------------------------------------------------------------------------
    // [#1459] distribute members to avoid static initialisers > 64kb
    // -------------------------------------------------------------------------

    private static class Indexes0 {
        public static Index FLYWAY_SCHEMA_HISTORY_S_IDX = Internal.createIndex("flyway_schema_history_s_idx", FlywaySchemaHistory.FLYWAY_SCHEMA_HISTORY, new OrderField[] { FlywaySchemaHistory.FLYWAY_SCHEMA_HISTORY.SUCCESS }, false);
        public static Index SYS_HOST_IDX = Internal.createIndex("sys_host_idx", Systems.SYSTEMS, new OrderField[] { Systems.SYSTEMS.HOST }, false);
        public static Index SYS_OWNER_IDX = Internal.createIndex("sys_owner_idx", Systems.SYSTEMS, new OrderField[] { Systems.SYSTEMS.OWNER }, false);
        public static Index SYS_TAGS_IDX = Internal.createIndex("sys_tags_idx", Systems.SYSTEMS, new OrderField[] { Systems.SYSTEMS.TAGS }, false);
        public static Index SYS_TENANT_NAME_IDX = Internal.createIndex("sys_tenant_name_idx", Systems.SYSTEMS, new OrderField[] { Systems.SYSTEMS.TENANT, Systems.SYSTEMS.ID }, false);
    }
}
