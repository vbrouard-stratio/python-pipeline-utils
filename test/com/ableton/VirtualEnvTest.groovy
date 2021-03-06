package com.ableton

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test


/**
 * Tests for the VirtualEnv class.
 */
class VirtualEnvTest extends BasePipelineTest {
  Object script

  @Override
  @Before
  @SuppressWarnings('ThrowException')
  void setUp() {
    super.setUp()

    this.script = loadScript('test/resources/EmptyPipeline.groovy')
    assertNotNull(script)
    script.env = ['BUILD_NUMBER': 1, 'JOB_BASE_NAME': 'mock']

    helper.registerAllowedMethod('error', [String]) { message ->
      throw new Exception(message)
    }
  }

  @Test
  void cleanup() {
    script.env['TEMP'] = 'C:\\Users\\whatever\\AppData\\Temp'

    VirtualEnv venv = new VirtualEnv(script, 'python3.6')

    venv.cleanup()
  }

  @Test
  void create() {
    script.env['TEMP'] = 'C:\\Users\\whatever\\AppData\\Temp'
    String python = 'python2.7'

    VirtualEnv venv = new VirtualEnv(script, python)

    helper.addShMock("virtualenv --python=${python} ${venv.destDir}", '', 0)
    VirtualEnv createdVenv = VirtualEnv.create(script, python)
    assertEquals(venv.destDir, createdVenv.destDir)
  }

  @Test
  void createPyenv() {
    String pythonVersion = '1.2.3'
    String pyenvRoot = '/mock/pyenv/root'
    helper.registerAllowedMethod('fileExists', [String]) { return true }
    helper.registerAllowedMethod('isUnix', []) { return true }
    // Note: This empty string allows us to compensate for trailing whitespace, which is
    // needed to match the string given to the sh mock.
    String empty = ''
    helper.addShMock("""
      ${empty}
      export PYENV_ROOT=${pyenvRoot}
      export PATH=\$PYENV_ROOT/bin:\$PATH
      eval "\$(pyenv init -)"
    ${empty}
      pyenv install --skip-existing ${pythonVersion}
      pyenv shell ${pythonVersion}
      pip install virtualenv
      virtualenv /tmp/mock/1/${pythonVersion}
    """, '', 0)

    VirtualEnv venv = VirtualEnv.create(script, pythonVersion, pyenvRoot)

    assertTrue(venv.activateCommands.contains(pyenvRoot))
  }

  @Test(expected = Exception)
  void createPyenvInvalidRoot() {
    String pyenvRoot = '/mock/pyenv/root'
    helper.registerAllowedMethod('fileExists', [String]) { return false }
    helper.registerAllowedMethod('isUnix', []) { return true }

    VirtualEnv.create(script, '1.2.3', pyenvRoot)
  }

  @Test(expected = AssertionError)
  void createPyenvNoRoot() {
    helper.registerAllowedMethod('isUnix', []) { return true }

    VirtualEnv.create(script, '1.2.3', null)
  }

  @Test(expected = Exception)
  void createPyenvWindows() {
    helper.registerAllowedMethod('isUnix', []) { return false }

    VirtualEnv.create(script, '1.2.3', 'C:\\pyenv')
  }

  @Test
  void newObjectUnix() {
    helper.registerAllowedMethod('isUnix', []) { return true }

    VirtualEnv venv = new VirtualEnv(script, 'python2.7')

    assertNotNull(venv)
    assertNotNull(venv.script)
    assertNotNull(venv.destDir)
  }

  @Test
  void newObjectWindows() {
    script.env['TEMP'] = 'C:\\Users\\whatever\\AppData\\Temp'
    helper.registerAllowedMethod('isUnix', []) { return false }

    VirtualEnv venv = new VirtualEnv(script, 'python2.7')

    assertNotNull(venv)
    assertNotNull(venv.script)
    assertNotNull(venv.destDir)
  }

  @Test
  void newObjectWithAbsolutePath() {
    helper.registerAllowedMethod('isUnix', []) { return true }
    String python = '/usr/bin/python3.5'

    VirtualEnv venv = new VirtualEnv(script, python)

    // Expect that the dirname of the python installation is stripped from the
    // virtualenv directory, but that it still retains the correct python version.
    assertFalse(venv.destDir.contains('usr/bin'))
    assertTrue(venv.destDir.endsWith('python3.5'))
  }

  @Test
  void newObjectWithAbsolutePathWindows() {
    helper.registerAllowedMethod('isUnix', []) { return false }
    script.env['TEMP'] = 'C:\\Users\\whatever\\AppData\\Temp'
    String python = '/c/Python27/python'

    VirtualEnv venv = new VirtualEnv(script, python)

    assertFalse(venv.destDir.startsWith('/c'))
    assertTrue(venv.destDir.endsWith('python'))
  }

  @Test
  void newObjectWithNullPython() {
    boolean exceptionThrown = false
    try {
      new VirtualEnv(script, null)
    } catch (AssertionError error) {
      exceptionThrown = true
      assertNotNull(error)
    }
    assertTrue(exceptionThrown)
  }

  @Test
  void newObjectWithNullScript() {
    boolean exceptionThrown = false
    try {
      new VirtualEnv(null, 'python2.7')
    } catch (AssertionError error) {
      exceptionThrown = true
      assertNotNull(error)
    }
    assertTrue(exceptionThrown)
  }

  @Test
  void run() {
    String mockScriptCall = '''
      . /tmp/mock/1/python/bin/activate
      mock-script
    '''
    helper.addShMock(mockScriptCall, 'mock output', 0)
    helper.registerAllowedMethod('isUnix', []) { return true }

    new VirtualEnv(script, 'python').run('mock-script')

    assertEquals(1, helper.callStack.findAll { call ->
      call.methodName == 'sh'
    }.size())
  }

  @Test
  void runWithMap() {
    String mockScriptCall = '''
      . /tmp/mock/1/python/bin/activate
      mock-script
    '''
    helper.addShMock(mockScriptCall, 'mock output', 0)
    helper.registerAllowedMethod('isUnix', []) { return true }

    new VirtualEnv(script, 'python').run(script: 'mock-script')

    assertEquals(1, helper.callStack.findAll { call ->
      call.methodName == 'sh'
    }.size())
  }

  @Test
  void runWithMapReturnStatus() {
    String mockScriptCall = '''
      . /tmp/mock/1/python/bin/activate
      mock-script
    '''
    helper.addShMock(mockScriptCall, 'mock output', 1234)
    helper.registerAllowedMethod('isUnix', []) { return true }

    int result = new VirtualEnv(script, 'python')
      .run(script: 'mock-script', returnStatus: true)

    assertEquals(1, helper.callStack.findAll { call ->
      call.methodName == 'sh'
    }.size())
    assertEquals(1234, result)
  }

  @Test
  void runWithMapReturnStdout() {
    String mockScriptCall = '''
      . /tmp/mock/1/python/bin/activate
      mock-script
    '''
    helper.addShMock(mockScriptCall, 'mock output', 0)
    helper.registerAllowedMethod('isUnix', []) { return true }

    String result = new VirtualEnv(script, 'python')
      .run(script: 'mock-script', returnStdout: true)

    assertEquals(1, helper.callStack.findAll { call ->
      call.methodName == 'sh'
    }.size())
    assertEquals('mock output', result)
  }
}
