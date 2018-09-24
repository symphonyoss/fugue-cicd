/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

abstract class FuguePipelineTask extends JenkinsTask implements FuguePipelineOrTask, Serializable
{
  protected FuguePipeline pipeLine_
  
  public FuguePipelineTask(FuguePipelineOrTask pipeLine)
  {
    super(pipeLine.getTask())
    
    echo 'TA3'
    pipeLine_ = pipeLine.getPipeLine()
  }
  
  @Override
  public FuguePipeline getPipeLine()
  {
    return pipeLine_;
  }

  @Override
  public JenkinsTask getTask()
  {
    return this;
  }

  public void verifyUserAccess(String credentialId, String environmentType = null)
  {
    pipeLine_.verifyUserAccess(credentialId, environmentType)
  }
  
  public abstract void execute()
}
