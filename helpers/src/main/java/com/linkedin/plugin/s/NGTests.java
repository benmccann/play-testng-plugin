// Copyright 2012 LinkedIn
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.linkedin.plugin.s;

import com.linkedin.plugin.NGTestsBase;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestResult;
import play.api.Application;
import play.api.GlobalSettings;
import play.api.inject.guice.GuiceBuilder;
import play.api.test.Helpers;
import play.api.test.TestBrowser;
import play.api.test.TestServer;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;

import java.io.File;
import java.util.Optional;

import static play.test.Helpers.HTMLUNIT;

/**
 * Scala API to be extended by TestNG classes to use custom @WithFakeApplication/@WithTestServer annotations.
 */
public class NGTests extends NGTestsBase implements IHookable {

  private class AnnotationsReader extends NGTestsBase.AnnotationsReader<WithFakeApplication, WithTestServer> {

    public AnnotationsReader(ITestResult testResult) {
      super(testResult, WithFakeApplication.class, WithTestServer.class);
    }

    private Application buildFakeApplication(WithFakeApplication fa) {
      if (fa == null) {
        return null;
      }

      FakeApplicationFactory appFactory = instantiate(fa.appFactory());
      return appFactory.buildScalaApplication(new FakeApplicationFactoryArgs(
          new File(fa.path()),
          isDefined(fa.guiceBuilder()) ? Optional.of(fa.guiceBuilder()) : Optional.<Class<? extends GuiceBuilder>>empty(),
          isDefined(fa.withGlobal()) ? Optional.of(instantiate(fa.withGlobal())) : Optional.<GlobalSettings>empty(),
          getOverrides(),
          getConf(),
          getPlugins()
      ));
    }

    private TestServer buildTestServer(WithTestServer ts) {
      Application fake = buildFakeApplication(ts.fakeApplication());
      return new TestServer(ts.port(), fake, scala.Option.apply(null), scala.Option.apply(null));
    }
  }

  // XXX: Evil hack, may lead to race conditions...
  private TestBrowser _testBrowser = null;

  /**
   * Scala API: gets a TestBrowser for Scala test classes. (Can only be used with @WithTestServer)
   */
  protected TestBrowser browser() {
    if (_testBrowser == null)
      throw new RuntimeException("No TestBrowser available, test class or method must be annotated with @WithTestServer");
    return _testBrowser;
  }

  public void run(final IHookCallBack icb, final ITestResult testResult) {

    AnnotationsReader reader = new AnnotationsReader(testResult);

    WithFakeApplication fa = reader.getFakeAppAnnotation();
    WithTestServer ts = reader.getTestServerAnnotation();

    if (fa != null)
    {
      Application app = reader.buildFakeApplication(fa);
      Helpers.running(app, new AbstractFunction0() {
        @Override
        public Object apply() {
          icb.runTestMethod(testResult);
          return null;
        }
      });
    }
    else if (ts != null)
    {
      TestServer server = reader.buildTestServer(ts);
      // TODO: parameterize WebDriver
      Helpers.running(server, HTMLUNIT, new AbstractFunction1<TestBrowser, Object>() {
        @Override
        public Object apply(final TestBrowser browser) {
          _testBrowser = browser;
          icb.runTestMethod(testResult);
          return null;
        }
      });
    }
    else
    {
      icb.runTestMethod(testResult);
    }
  }
}
