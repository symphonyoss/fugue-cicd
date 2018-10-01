/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

class Default
{
  public static String  value(String var, String defaultValue)
  {
    if(var==null || "".equals(var.trim()))
      return defaultValue
    else
      return var
  }
  
  public static String  valueB(EnvActionImpl env, String paramName, String defaultValue)
  {
    String var = env.getProperty(paramName)
    
    if(var==null || "".equals(var.trim()))
      return defaultValue
    else
      return var
  }
}
