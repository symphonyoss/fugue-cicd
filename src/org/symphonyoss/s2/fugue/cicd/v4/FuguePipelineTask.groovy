/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v4

abstract class FuguePipelineTask extends JenkinsTask implements Serializable
{
  protected FuguePipeline pipeLine_
  
//  public FuguePipelineTask(FuguePipelineTask pipeLine)
//  {
//    super(pipeLine)
//    
//    echo 'TA3a'
//    pipeLine_ = pipeLine.pipeLine_
//  }
  
  public FuguePipelineTask(FuguePipeline pipeLine)
  {
    super(pipeLine)
    
    pipeLine_ = pipeLine
  }
  
//  public FuguePipelineTask(JenkinsTask task, FuguePipeline pipeLine)
//  {
//    super(task)
//    
//    echo 'TA3c'
//    pipeLine_ = pipeLine
//  }

  public void verifyUserAccess(String credentialId, String environmentType = null)
  {
    pipeLine_.verifyUserAccess(credentialId, environmentType)
  }
  
  ///public abstract void execute()
}
