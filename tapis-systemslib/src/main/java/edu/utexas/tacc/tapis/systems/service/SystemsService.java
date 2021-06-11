package edu.utexas.tacc.tapis.systems.service;

import edu.utexas.tacc.tapis.client.shared.exceptions.TapisClientException;
import edu.utexas.tacc.tapis.shared.exceptions.TapisException;
import edu.utexas.tacc.tapis.shared.threadlocal.OrderBy;
import edu.utexas.tacc.tapis.sharedapi.security.AuthenticatedUser;
import edu.utexas.tacc.tapis.systems.model.Credential;
import edu.utexas.tacc.tapis.systems.model.PatchSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem;
import edu.utexas.tacc.tapis.systems.model.TSystem.AuthnMethod;
import edu.utexas.tacc.tapis.systems.model.TSystem.Permission;
import org.jvnet.hk2.annotations.Contract;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Set;

/*
 * Interface for Systems Service
 * Annotate as an hk2 Contract in case we have multiple implementations
 */
@Contract
public interface SystemsService
{
  void createSystem(AuthenticatedUser authenticatedUser, TSystem system, String scrubbedText)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException, IllegalArgumentException;

  void updateSystem(AuthenticatedUser authenticatedUser, PatchSystem patchSystem, String scrubbedText)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException, IllegalArgumentException, NotFoundException;

  void putSystem(AuthenticatedUser authenticatedUser, TSystem putSystem, String scrubbedText)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException, IllegalArgumentException, NotFoundException;

  int enableSystem(AuthenticatedUser authenticatedUser, String tenantId, String systemId)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException, IllegalArgumentException, NotFoundException;

  int disableSystem(AuthenticatedUser authenticatedUser, String tenantId, String systemId)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException, IllegalArgumentException, NotFoundException;

  int deleteSystem(AuthenticatedUser authenticatedUser, String tenantId, String systemId)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException, IllegalArgumentException, NotFoundException;

  int undeleteSystem(AuthenticatedUser authenticatedUser, String tenantId, String systemId)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException, IllegalArgumentException, NotFoundException;

  int changeSystemOwner(AuthenticatedUser authenticatedUser, String tenantId, String systemId, String newOwnerName)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException, IllegalArgumentException, NotFoundException;

  boolean checkForSystem(AuthenticatedUser authenticatedUser, String tenantId, String systemId)
          throws TapisException, TapisClientException, NotAuthorizedException;

  boolean checkForSystem(AuthenticatedUser authenticatedUser, String tenantId, String systemId, boolean includeDeleted)
          throws TapisException, TapisClientException, NotAuthorizedException;

  boolean isEnabled(AuthenticatedUser authenticatedUser, String tenantId, String systemId)
          throws TapisException, TapisClientException, NotAuthorizedException;

  TSystem getSystem(AuthenticatedUser authenticatedUser, String tenantId, String systemId, boolean getCreds, AuthnMethod authnMethod,
                    boolean requireExecPerm)
          throws TapisException, TapisClientException, NotAuthorizedException;

  int getSystemsTotalCount(AuthenticatedUser authenticatedUser, String tenantId, List<String> searchList, List<OrderBy> orderByList,
                           String startAfter, boolean showDeleted) throws TapisException, TapisClientException;

  List<TSystem> getSystems(AuthenticatedUser authenticatedUser, String tenantId, List<String> searchList, int limit,
                           List<OrderBy> orderByList, int skip, String startAfter, boolean showDeleted)
          throws TapisException, TapisClientException;

  List<TSystem> getSystemsUsingSqlSearchStr(AuthenticatedUser authenticatedUser, String tenantId, String searchStr, int limit,
                                        List<OrderBy> orderByList, int skip, String startAfter, boolean showDeleted)
          throws TapisException, TapisClientException;

  List<TSystem> getSystemsSatisfyingConstraints(AuthenticatedUser authenticatedUser, String tenantId, String matchStr)
          throws TapisException, TapisClientException;

  String getSystemOwner(AuthenticatedUser authenticatedUser, String tenantId, String systemId)
          throws TapisException, TapisClientException, NotAuthorizedException;

  void grantUserPermissions(AuthenticatedUser authenticatedUser, String tenantId, String systemId, String userName, Set<Permission> permissions, String updateText)
          throws TapisException, TapisClientException, NotAuthorizedException;

  int revokeUserPermissions(AuthenticatedUser authenticatedUser, String tenantId, String systemId, String userName, Set<Permission> permissions, String updateText)
          throws TapisException, TapisClientException, NotAuthorizedException;

  Set<Permission> getUserPermissions(AuthenticatedUser authenticatedUser, String tenantId, String systemId, String userName)
          throws TapisException, TapisClientException, NotAuthorizedException;

  void createUserCredential(AuthenticatedUser authenticatedUser, String tenantId, String systemId, String userName, Credential credential, String scrubbedText)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException;

  int deleteUserCredential(AuthenticatedUser authenticatedUser, String tenantId, String systemId, String userName)
          throws TapisException, TapisClientException, NotAuthorizedException, IllegalStateException;

  Credential getUserCredential(AuthenticatedUser authenticatedUser, String tenantId, String systemId, String userName, AuthnMethod authnMethod)
          throws TapisException, TapisClientException, NotAuthorizedException;
}
