package edu.utexas.tacc.tapis.systems.dao;

import edu.utexas.tacc.tapis.search.parser.ASTNode;
import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import edu.utexas.tacc.tapis.shared.threadlocal.OrderBy;
import edu.utexas.tacc.tapis.sharedapi.security.AuthenticatedUser;
import edu.utexas.tacc.tapis.systems.model.PatchSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.SystemOperation;

import java.util.List;
import java.util.Set;

public interface SystemsDao
{
  boolean createSystem(AuthenticatedUser authenticatedUser, TSystem system, String createJsonStr, String scrubbedText)
          throws TapisException, IllegalStateException;

  void updateSystem(AuthenticatedUser authenticatedUser, TSystem patchedSystem, PatchSystem patchSystem,
                    String updateJsonStr, String scrubbedText)
          throws TapisException, IllegalStateException;

  void updateSystemOwner(AuthenticatedUser authenticatedUser, String id, String newOwnerName) throws TapisException;

  void updateEnabled(AuthenticatedUser authenticatedUser, String id, boolean enabled) throws TapisException;

  int softDeleteSystem(AuthenticatedUser authenticatedUser, String systemId) throws TapisException;

  void addUpdateRecord(AuthenticatedUser authenticatedUser, String tenant, String id, SystemOperation op,
                       String upd_json, String upd_text) throws TapisException;

  int hardDeleteSystem(String tenant, String id) throws TapisException;

  Exception checkDB();

  void migrateDB() throws TapisException;

  boolean checkForSystem(String tenant, String id, boolean includeDeleted) throws TapisException;

  TSystem getSystem(String tenant, String id) throws TapisException;

  TSystem getSystem(String tenant, String id, boolean includeDeleted) throws TapisException;

  int getSystemsCount(String tenant, List<String> searchList, ASTNode searchAST, Set<String> setOfIDs,
                      List<OrderBy> orderByList, String startAfter) throws TapisException;

  List<TSystem> getSystems(String tenant, List<String> searchList, ASTNode searchAST, Set<String> setOfIDs, int limit,
                           List<OrderBy> orderByList, int skip, String startAfter)
          throws TapisException;

  List<TSystem> getSystemsSatisfyingConstraints(String tenant, ASTNode matchAST, Set<String> setOfIDs) throws TapisException;

  String getSystemOwner(String tenant, String id) throws TapisException;

  String getSystemEffectiveUserId(String tenant, String id) throws TapisException;

  AuthnMethod getSystemDefaultAuthnMethod(String tenant, String id) throws TapisException;
}
