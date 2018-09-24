/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

interface FuguePipelineOrTask
{
  FuguePipeline getPipeLine()
  
  JenkinsTask   getTask()
}
