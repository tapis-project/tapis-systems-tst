package edu.utexas.tacc.tapis.systems.dao;

import edu.utexas.tacc.tapis.search.parser.ASTNode;
import edu.utexas.tacc.tapis.search.parser.ASTParser;
import edu.utexas.tacc.tapis.shared.threadlocal.TapisThreadContext;
import edu.utexas.tacc.tapis.shared.utils.TapisUtils;
import edu.utexas.tacc.tapis.sharedapi.security.AuthenticatedUser;
import edu.utexas.tacc.tapis.systems.IntegrationUtils;
import edu.utexas.tacc.tapis.systems.model.TSystem;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

import static edu.utexas.tacc.tapis.shared.threadlocal.SearchParameters.*;
import static edu.utexas.tacc.tapis.systems.IntegrationUtils.*;

/**
 * Test the SystemsDao getTSystems call when using an ASTNode for various search use cases against a DB running locally
 * NOTE: This test pre-processes the sql-like search string just as is done in SystemsServiceImpl before it calls the Dao.
 *       For this reason there is currently no need to have a SearchSystemsTest suite.
 *       If this changes then we will need to create another suite and move the test data into IntegrationUtils so that
 *       it can be re-used.
 * TODO: Support NLIKE, NIN, NBETWEEN
 */
@Test(groups={"integration"})
public class SearchASTDaoTest
{
  private SystemsDaoImpl dao;
  private AuthenticatedUser authenticatedUser;

  // Test data
  private static final String testKey = "SrchAST";
  private static final String sysNameLikeAll = sq("%" + testKey + "%");

  // Strings for searches involving special characters
  private static final String specialChar3Str = ".:_"; // Values containing these must be surrounded by single quotes.
  private static final String specialChar7Str = ",()~*!\\"; // These 7 may need escaping
  private static final String specialChar7LikeSearchStr = ",()~*!\\\\"; // Only \ needs escaping for LIKE/NLIKE

  // String for search involving an escaped comma in a list of values
  private static final String escapedCommanInListValue = "abc\\,def";
//
//  // Timestamps in various formats
  private static final String longPast1 =   "1800-01-01T00:00:00.123456Z";
  private static final String farFuture1 =  "2200-04-29T14:15:52.123456-06:00";
  private static final String farFuture2 =  "2200-04-29T14:15:52.123Z";
  private static final String farFuture3 =  "2200-04-29T14:15:52.123";
  private static final String farFuture4 =  "2200-04-29T14:15:52-06:00";
  private static final String farFuture5 =  "2200-04-29T14:15:52";
  private static final String farFuture6 =  "2200-04-29T14:15-06:00";
  private static final String farFuture7 =  "2200-04-29T14:15";
  private static final String farFuture8 =  "2200-04-29T14-06:00";
  private static final String farFuture9 =  "2200-04-29T14";
  private static final String farFuture10 = "2200-04-29-06:00";
  private static final String farFuture11 = "2200-04-29";
  private static final String farFuture12 = "2200-04Z";
  private static final String farFuture13 = "2200-04";
  private static final String farFuture14 = "2200Z";
  private static final String farFuture15 = "2200";

  int numSystems = 20;
  TSystem[] systems = IntegrationUtils.makeSystems(numSystems, testKey);

  LocalDateTime createBegin;
  LocalDateTime createEnd;

  @BeforeSuite
  public void setup() throws Exception
  {
    System.out.println("Executing BeforeSuite setup method: " + SearchASTDaoTest.class.getSimpleName());
    dao = new SystemsDaoImpl();
    // Initialize authenticated user
    authenticatedUser = new AuthenticatedUser(apiUser, tenantName, TapisThreadContext.AccountType.user.name(), null, apiUser, tenantName, null, null, null);

    // Cleanup anything leftover from previous failed run
    teardown();

    // Vary port # for checking numeric relational searches
    for (int i = 0; i < numSystems; i++) { systems[i].setPort(i+1); }
    // For half the systems change the owner
    for (int i = 0; i < numSystems/2; i++) { systems[i].setOwner(owner2); }

    // For one system update description to have some special characters. 7 special chars in value: ,()~*!\
    //   and update workingDir for testing an escaped comma in a list value
    systems[numSystems-1].setDescription(specialChar7Str);
    systems[numSystems-1].setJobWorkingDir(escapedCommanInListValue);

    systems[numSystems-2].setDescription(specialChar3Str);

    // Create all the systems in the dB using the in-memory objects, recording start and end times
    createBegin = TapisUtils.getUTCTimeNow();
    Thread.sleep(500);
    for (TSystem sys : systems)
    {
      boolean itemCreated = dao.createSystem(authenticatedUser, sys, gson.toJson(sys), scrubbedJson);
      Assert.assertTrue(itemCreated, "Item not created, id: " + sys.getId());
    }
    Thread.sleep(500);
    createEnd = TapisUtils.getUTCTimeNow();
  }

  @AfterSuite
  public void teardown() throws Exception {
    System.out.println("Executing AfterSuite teardown for " + SearchASTDaoTest.class.getSimpleName());
    //Remove all objects created by tests
    for (TSystem sys : systems)
    {
      dao.hardDeleteSystem(tenantName, sys.getId());
    }

    TSystem tmpSystem = dao.getSystem(tenantName, systems[0].getId(), true);
    Assert.assertNull(tmpSystem, "System not deleted. System name: " + systems[0].getId());
  }

  /*
   * Check valid cases
   */
  @Test(groups={"integration"})
  public void testValidCases() throws Exception
  {
    String[] hostNames = new String[numSystems];
    for (int i = 0; i < numSystems; i++) hostNames[i] = systems[i].getHost();
    TSystem sys0 = systems[0];
    String sys0Name = sys0.getId();
    String nameList = "('noSuchName1','noSuchName2'," + sq(sys0Name) + ",'noSuchName3')";
    // Create all input and validation data for tests
    // NOTE: Some cases require "id LIKE " + sysNameLikeAll in the list of conditions since maven runs the tests in
    //       parallel and not all attribute names are unique across integration tests
    class CaseData {public final int count; public final String sqlSearchStr; CaseData(int c, String s) { count = c; sqlSearchStr = s; }}
    var validCaseInputs = new HashMap<Integer, CaseData>();
    // TODO Support NLIKE, NBETWEEN, NIN
    // Test basic types and operators
    validCaseInputs.put( 1,new CaseData(1, "id = " + sq(sys0Name))); // 1 has specific name
    validCaseInputs.put( 2,new CaseData(1, "description = " + sq(sys0.getDescription())));
    validCaseInputs.put( 3,new CaseData(1, "host = " + sq(sys0.getHost())));
    validCaseInputs.put( 4,new CaseData(1, "bucket_name = " + sq(sys0.getBucketName())));
    validCaseInputs.put( 5,new CaseData(1, "root_dir = " + sq(sys0.getRootDir())));
    validCaseInputs.put( 6,new CaseData(1, "job_working_dir = " + sq(sys0.getJobWorkingDir())));
    validCaseInputs.put( 7,new CaseData(20, "id LIKE " + sysNameLikeAll + "AND batch_scheduler = " + sq(sys0.getBatchScheduler().name())));
    validCaseInputs.put( 8,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + "AND batch_default_logical_queue = " + sq(sys0.getBatchDefaultLogicalQueue())));
    validCaseInputs.put(10,new CaseData(numSystems/2, "id LIKE " + sysNameLikeAll + " AND owner = " + sq(owner1)));  // Half owned by one user
    validCaseInputs.put(11,new CaseData(numSystems/2, "id LIKE " + sysNameLikeAll + " AND owner = " + sq(owner2))); // and half owned by another
    validCaseInputs.put(12,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND enabled = true"));  // All are enabled
    validCaseInputs.put(13,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND deleted = false")); // none are deleted
    validCaseInputs.put(14,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND deleted <> true")); // none are deleted
    validCaseInputs.put(15,new CaseData(0, "id LIKE " + sysNameLikeAll + " AND deleted = true"));           // none are deleted
    validCaseInputs.put(16,new CaseData(1, "id LIKE " + sq(sys0Name)));
    validCaseInputs.put(17,new CaseData(0, "id LIKE 'NOSUCHSYSTEMxFM2c29bc8RpKWeE2sht7aZrJzQf3s'"));
    validCaseInputs.put(18,new CaseData(numSystems, "id LIKE " + sysNameLikeAll));
//    validCaseInputs.put(19,new CaseData(numSystems-1, "id LIKE " + sysNameLikeAll + " AND id NLIKE " + sq(sys0Name))); // TODO support NLIKE
    validCaseInputs.put(20,new CaseData(1, "id LIKE " + sysNameLikeAll + " AND id IN " + nameList));
//    validCaseInputs.put(21,new CaseData(numSystems-1, "id LIKE " + sysNameLikeAll + " AND id.NIN." + nameList)); // TODO support NIN
    validCaseInputs.put(22,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND system_type = LINUX"));
    validCaseInputs.put(23,new CaseData(numSystems/2, "id LIKE " + sysNameLikeAll +  " AND system_type = LINUX AND owner <> " + sq(owner2)));

    // Check various special characters require modified handling in AST. Special chars in value: _:.
    validCaseInputs.put(30,new CaseData(1, "id LIKE " + sysNameLikeAll + " AND description = " + sq(specialChar3Str)));
    validCaseInputs.put(31,new CaseData(1, "id LIKE " + sysNameLikeAll + " AND description LIKE " + sq(specialChar3Str)));

    // Test numeric relational
    validCaseInputs.put(50,new CaseData(numSystems/2, "id LIKE " + sysNameLikeAll + " AND port BETWEEN '1' AND '" + numSystems/2 + "'"));
    validCaseInputs.put(51,new CaseData(numSystems/2-1, "id LIKE " + sysNameLikeAll + " AND port BETWEEN '2' AND '" + numSystems/2 + "'"));
//    validCaseInputs.put(52,new CaseData(numSystems/2, "id LIKE " + sysNameLikeAll + " AND port NBETWEEN '1' AND '" + numSystems/2 + "'"));

    validCaseInputs.put(53,new CaseData(13, "id LIKE " + sysNameLikeAll + " AND enabled = true AND port <= 13"));
    validCaseInputs.put(54,new CaseData(5, "id LIKE " + sysNameLikeAll + " AND enabled = true AND port > 1 AND port < 7"));
//    // Test char relational
    validCaseInputs.put(70,new CaseData(1, "id LIKE " + sysNameLikeAll + " AND host < " + sq(hostNames[1])));
    validCaseInputs.put(71,new CaseData(numSystems-8, "id LIKE " + sysNameLikeAll + " AND enabled = true AND host > " + sq(hostNames[7])));
    validCaseInputs.put(72,new CaseData(5, "id LIKE " + sysNameLikeAll + " AND host > " + sq(hostNames[1]) + " AND host < " + sq(hostNames[7])));
    validCaseInputs.put(73,new CaseData(0, "id LIKE " + sysNameLikeAll + " AND host < " + sq(hostNames[1]) + " AND host > " + sq(hostNames[7])));
    validCaseInputs.put(74,new CaseData(7, "id LIKE " + sysNameLikeAll + " AND host BETWEEN " + sq(hostNames[1]) + " AND " + sq(hostNames[7])));
//    validCaseInputs.put(75,new CaseData(numSystems-7, "id LIKE " + sysNameLikeAll + " AND host NBEWTEEN " + sq(hostNames[1]) + " AND " + sq(hostNames[7])));

//    // Test timestamp relational
    validCaseInputs.put(90,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created > " + sq(longPast1)));
    validCaseInputs.put(91,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture1)));
    validCaseInputs.put(92,new CaseData(0, "id LIKE " + sysNameLikeAll + " AND created <= " + sq(longPast1)));
    validCaseInputs.put(93,new CaseData(0, "id LIKE " + sysNameLikeAll + " AND created >= " + sq(farFuture1)));
    validCaseInputs.put(94,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created BETWEEN " + sq(longPast1) + " AND " + sq(farFuture1)));
//    validCaseInputs.put(95,new CaseData(0, "id LIKE " + sysNameLikeAll + " AND created NBETWEEN " + sq(longPast1) + " AND " + sq(farFuture1)));
    // Variations of timestamp format
    validCaseInputs.put(96,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture2)));
    validCaseInputs.put(97,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture3)));
    validCaseInputs.put(98,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture4)));
    validCaseInputs.put(99,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture5)));
    validCaseInputs.put(100,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture6)));
    validCaseInputs.put(101,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture7)));
    validCaseInputs.put(102,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture8)));
    validCaseInputs.put(103,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture9)));
    validCaseInputs.put(104,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture10)));
    validCaseInputs.put(105,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture11)));
    validCaseInputs.put(106,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture12)));
    validCaseInputs.put(107,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture13)));
    validCaseInputs.put(108,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture14)));
    validCaseInputs.put(109,new CaseData(numSystems, "id LIKE " + sysNameLikeAll + " AND created < " + sq(farFuture15)));

    //    // Test wildcards
    validCaseInputs.put(130,new CaseData(numSystems, "enabled = true AND host LIKE 'host" + testKey + "%'"));
//    validCaseInputs.put(131,new CaseData(0, "id LIKE " + sysNameLikeAll + " AND enabled = true AND host NLIKE 'host" + testKey + "%'"));
    validCaseInputs.put(132,new CaseData(9, "id LIKE " + sysNameLikeAll + " AND enabled = true AND host LIKE 'host" + testKey + "00_.test.org'"));
//    validCaseInputs.put(133,new CaseData(11, "id LIKE " + sysNameLikeAll + " AND enabled = true AND host NLIKE 'host" + testKey + "00_.test.org'"));

    // Check various special characters in description. 7 special chars in value: ,()~*!\
    validCaseInputs.put(171,new CaseData(1, "id LIKE " + sysNameLikeAll + " AND description LIKE " + sq(specialChar7LikeSearchStr)));
//    validCaseInputs.put(172,new CaseData(numSystems-1, "id LIKE " + sysNameLikeAll + " AND description NLIKE " + sq(specialChar7LikeSearchStr2)));
    validCaseInputs.put(173,new CaseData(1, "id LIKE " + sysNameLikeAll + " AND description = " + sq(specialChar7Str)));
    validCaseInputs.put(174,new CaseData(numSystems-1, "id LIKE " + sysNameLikeAll + " AND description <> " + sq(specialChar7Str)));
//    // Escaped comma in a list of values
    validCaseInputs.put(200,new CaseData(1, "id LIKE " + sysNameLikeAll + " AND job_working_dir IN (" + sq("noSuchDir") + "," + sq(escapedCommanInListValue) + ")"));

    // Iterate over valid cases
    for (Map.Entry<Integer,CaseData> item : validCaseInputs.entrySet())
    {
      CaseData cd = item.getValue();
      int caseNum = item.getKey();
      System.out.println("Checking case # " + caseNum + " Input:        " + cd.sqlSearchStr);
      // Build an AST from the sql-like search string
      ASTNode searchAST = ASTParser.parse(cd.sqlSearchStr);
      System.out.println("  Created AST with leaf node count: " + searchAST.countLeaves());
      List<TSystem> searchResults = dao.getSystems(tenantName, null, searchAST, null, DEFAULT_LIMIT,
              orderByListNull, DEFAULT_SKIP, startAfterNull);
      System.out.println("  Result size: " + searchResults.size());
      assertEquals(searchResults.size(), cd.count, "SearchASTDaoTest.testValidCases: Incorrect result count for case number: " + caseNum);
    }
  }

  /*
   * Test pagination options: limit, skip
   */
  @Test(groups={"integration"})
  public void testLimitSkip() throws Exception
  {
    String searchStr = "id LIKE " + sysNameLikeAll;
    ASTNode searchAST = ASTParser.parse(searchStr);
    System.out.println("SearchSQL: " + searchStr);
    List<TSystem> searchResults;

    int limit = -1;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), numSystems, "Incorrect result count");
    limit = 0;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    limit = 1;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    limit = 5;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    limit = 19;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    limit = 20;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    limit = 200;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), numSystems, "Incorrect result count");
    // Test limit + skip combination that reduces result size
    int resultSize = 3;
    limit = numSystems;
    int skip = limit - resultSize;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, skip, startAfterNull);
    assertEquals(searchResults.size(), resultSize, "Incorrect result count");

    // Check some corner cases
    limit = 100;
    skip = 0;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, skip, startAfterNull);
    assertEquals(searchResults.size(), numSystems, "Incorrect result count");
    limit = 0;
    skip = 1;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, skip, startAfterNull);
    assertEquals(searchResults.size(), 0, "Incorrect result count");
    limit = 10;
    skip = 15;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, skip, startAfterNull);
    assertEquals(searchResults.size(), numSystems - skip, "Incorrect result count");
    limit = 10;
    skip = 100;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListNull, skip, startAfterNull);
    assertEquals(searchResults.size(), 0, "Incorrect result count");
  }

  /*
   * Test sorting: limit, orderBy, skip
   */
  @Test(groups={"integration"})
  public void testSortingSkip() throws Exception
  {
    String searchStr = "id LIKE " + sysNameLikeAll;
    ASTNode searchAST = ASTParser.parse(searchStr);
    System.out.println("SearchSQL: " + searchStr);
    List<TSystem> searchResults;

    int limit;
    int skip;
    // Sort and check order with no limit or skip
    searchResults = dao.getSystems(tenantName, null, searchAST, null, DEFAULT_LIMIT, orderByListAsc, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), numSystems, "Incorrect result count");
    checkOrder(searchResults, 1, numSystems);
    searchResults = dao.getSystems(tenantName, null, searchAST, null, DEFAULT_LIMIT, orderByListDesc, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), numSystems, "Incorrect result count");
    checkOrder(searchResults, numSystems, 1);
    // Sort and check order with limit and no skip
    limit = 4;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListAsc, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    checkOrder(searchResults, 1, limit);
    limit = 19;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListDesc, DEFAULT_SKIP, startAfterNull);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    checkOrder(searchResults, numSystems, numSystems - (limit-1));
    // Sort and check order with limit and skip
    limit = 2;
    skip = 5;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListAsc, skip, startAfterNull);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    // Should get systems named SrchGet_006 to SrchGet_007
    checkOrder(searchResults, skip + 1, skip + limit);
    limit = 4;
    skip = 3;
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListDesc, skip, startAfterNull);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    // Should get systems named SrchGet_017 to SrchGet_014
    checkOrder(searchResults, numSystems - skip, numSystems - limit);
  }

  /*
   * Test sorting: limit, orderBy, startAfter
   */
  @Test(groups={"integration"})
  public void testSortingStartAfter() throws Exception
  {
    String searchStr = "id LIKE " + sysNameLikeAll;
    ASTNode searchAST = ASTParser.parse(searchStr);
    System.out.println("SearchSQL: " + searchStr);
    List<TSystem> searchResults;

    int limit;
    int startAfterIdx;
    String startAfter;
    // Sort and check order with limit and startAfter
    limit = 2;
    startAfterIdx = 5;
    startAfter = getSysName(testKey, startAfterIdx);
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListAsc, DEFAULT_SKIP, startAfter);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    // Should get systems named SrchGet_006 to SrchGet_007
    checkOrder(searchResults, startAfterIdx + 1, startAfterIdx + limit);
    limit = 4;
    startAfterIdx = 18;
    int startWith = numSystems - startAfterIdx + 1;
    startAfter = getSysName(testKey, startAfterIdx);
    searchResults = dao.getSystems(tenantName, null, searchAST, null, limit, orderByListDesc, DEFAULT_SKIP, startAfter);
    assertEquals(searchResults.size(), limit, "Incorrect result count");
    // Should get systems named SrchGet_017 to SrchGet_014
    checkOrder(searchResults, numSystems - startWith, numSystems - limit);
  }

  /* ********************************************************************** */
  /*                             Private Methods                            */
  /* ********************************************************************** */

  /**
   * Check that results were sorted in correct order when sorting on system name
   */
  private void checkOrder(List<TSystem> searchResults, int start, int end)
  {
    int idx = 0; // Position in result
    // Name should match for loop counter i
    if (start < end)
    {
      for (int i = start; i <= end; i++)
      {
        String sysName = getSysName(testKey, i);
        assertEquals(searchResults.get(idx).getId(), sysName, "Incorrect system name at position: " + (idx+1));
        idx++;
      }
    }
    else
    {
      for (int i = start; i >= end; i--)
      {
        String sysName = getSysName(testKey, i);
        assertEquals(searchResults.get(idx).getId(), sysName, "Incorrect system name at position: " + (idx+1));
        idx++;
      }
    }
  }

  /**
   * Wrap a string in single quotes
   */
  private static String sq(String s) { return "'" + s + "'"; }
}
