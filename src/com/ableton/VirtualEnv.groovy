package com.ableton


/**
 * Provides a minimal wrapper around a Python Virtualenv environment. The Virtualenv is
 * stored in the job's temporary directory and is unique for each build number.
 */
class VirtualEnv implements Serializable {
  def script
  String destDir

  VirtualEnv(script, String python) {
    assert script
    assert python

    this.script = script

    final PATH_SEP = script.isUnix() ? '/' : '\\'
    this.destDir = script.pwd(tmp: true) +
      PATH_SEP +
      script.env.BUILD_NUMBER +
      PATH_SEP +
      python
  }

  static create(script, python) {
    def venv = new VirtualEnv(script, python)
    venv.script.sh("virtualenv --python=${python} ${venv.destDir}")
    return venv
  }

  def run(String command) {
    script.sh("""
      . ${destDir}/bin/activate
      ${command}
    """)
  }
}
