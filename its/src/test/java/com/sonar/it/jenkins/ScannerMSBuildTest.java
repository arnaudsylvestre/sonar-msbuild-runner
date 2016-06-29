/*
 * Scanner for MSBuild :: Integration Tests
 * Copyright (C) 2016-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.sonar.it.jenkins;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.ScannerForMSBuild;
import com.sonar.orchestrator.junit.SingleStartExternalResource;
import com.sonar.orchestrator.locator.FileLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.issue.Issue;
import org.sonar.wsclient.issue.IssueQuery;

import static org.assertj.core.api.Assertions.assertThat;

public class ScannerMSBuildTest {

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  public static Orchestrator ORCHESTRATOR;

  @ClassRule
  public static SingleStartExternalResource resource = new SingleStartExternalResource() {

    @Override
    protected void beforeAll() {
      Path modifiedCs = TestUtils.prepareCSharpPlugin(temp);
      ORCHESTRATOR = Orchestrator.builderEnv()
        .addPlugin(FileLocation.of(modifiedCs.toFile()))
        .build();
      ORCHESTRATOR.start();
    }

    @Override
    protected void afterAll() {
      ORCHESTRATOR.stop();
    }
  };

  @Test
  public void testSample() throws Exception {
    ORCHESTRATOR.getServer().restoreProfile(FileLocation.of("projects/ProjectUnderTest/TestQualityProfile.xml"));
    ORCHESTRATOR.getServer().provisionProject("sample", "sample");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile("sample", "cs", "ProfileForTest");

    Path projectDir = TestUtils.projectDir(temp, "ProjectUnderTest");
    ORCHESTRATOR.executeBuild(ScannerForMSBuild.create(projectDir.toFile())
      .addArgument("begin")
      .setProjectKey("sample")
      .setProjectName("sample")
      .setProjectVersion("1.0"));

    TestUtils.runMSBuild(ORCHESTRATOR, projectDir, "/t:Rebuild");

    ORCHESTRATOR.executeBuild(ScannerForMSBuild.create(projectDir.toFile())
      .addArgument("end"));

    List<Issue> issues = ORCHESTRATOR.getServer().wsClient().issueClient().find(IssueQuery.create()).list();
    assertThat(issues).hasSize(4);
  }
  
  @Test
  public void testParameters() throws Exception {
    ORCHESTRATOR.getServer().restoreProfile(FileLocation.of("projects/ProjectUnderTest/TestQualityProfileParameters.xml"));
    ORCHESTRATOR.getServer().provisionProject("parameters", "parameters");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile("parameters", "cs", "ProfileForTestParameters");

    Path projectDir = TestUtils.projectDir(temp, "ProjectUnderTest");
    ORCHESTRATOR.executeBuild(ScannerForMSBuild.create(projectDir.toFile())
      .addArgument("begin")
      .setProjectKey("parameters")
      .setProjectName("parameters")
      .setProjectVersion("1.0"));

    TestUtils.runMSBuild(ORCHESTRATOR, projectDir, "/t:Rebuild");

    ORCHESTRATOR.executeBuild(ScannerForMSBuild.create(projectDir.toFile())
      .addArgument("end"));

    List<Issue> issues = ORCHESTRATOR.getServer().wsClient().issueClient().find(IssueQuery.create()).list();
    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).message()).isEqualTo("Method has 3 parameters, which is greater than the 2 authorized.");
    assertThat(issues.get(0).ruleKey()).isEqualTo("S107");
  }
  
  @Test
  public void testVerbose() throws IOException {
    ORCHESTRATOR.getServer().restoreProfile(FileLocation.of("projects/ProjectUnderTest/TestQualityProfile.xml"));
    ORCHESTRATOR.getServer().provisionProject("verbose", "verbose");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile("verbose", "cs", "ProfileForTest");

    Path projectDir = TestUtils.projectDir(temp, "ProjectUnderTest");
    BuildResult result = ORCHESTRATOR.executeBuild(ScannerForMSBuild.create(projectDir.toFile())
      .addArgument("begin")
      .setProjectKey("verbose")
      .setProjectName("verbose")
      .setProjectVersion("1.0")
      .addArgument("/d:sonar.verbose=true"));
    
    assertThat(result.getLogs()).contains("Downloading from http://localhost");
    assertThat(result.getLogs()).contains("sonar.verbose=true was specified - setting the log verbosity to 'Debug'");
  }
}
