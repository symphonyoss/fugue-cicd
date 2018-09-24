/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

import org.jenkinsci.plugins.workflow.cps.DSL
import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

class JenkinsTask implements Serializable
{
  protected EnvActionImpl     env_
  protected DSL               steps_
  
  public JenkinsTask(EnvActionImpl env, DSL steps)
  {
    env_    = env
    steps_  = steps
  }
  
  public JenkinsTask(JenkinsTask task)
  {
    env_    = task.env_
    steps_  = task.steps_
  }
  
  public void echo(String message)
  {
    steps_."echo" message
  }
  
  public def sh(String script, boolean returnStdout = false, boolean returnStatus = false)
  {
    return steps_."sh"(script: script, returnStdout: returnStdout, returnStatus: returnStatus)
  }
  
  public def sh(Map args)
  {
    return steps_."sh"(args)
  }
  
  public def readJSON(Map args)
  {
    return steps_."readJSON"(args)
  }
  
  public def withCredentials(Map args)
  {
    return steps_."withCredentials"(args)
  }
  
  public def git(Map args)
  {
    return steps_."git"(args)
  }
  
  public def string(Map args)
  {
    return steps_."string"(args)
  }
  
  public def readFile(Map args)
  {
    return steps_."readFile"(args)
  }
  
  public def writeFile(Map args)
  {
    return steps_."writeFile"(args)
  }
}
