/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

import org.jenkinsci.plugins.workflow.cps.EnvActionImpl

class Default
{
  public static String  value(EnvActionImpl env, String paramName, String defaultValue)
  {
    String var = env.getProperty(paramName)
    
    if(var==null || "".equals(var.trim()))
      return defaultValue
    else
      return var
  }
  
  public static List<String>  choice(EnvActionImpl env, String paramName, List<String> choices)
  {
    String var = env.getProperty(paramName)
    
    if(var==null || "".equals(var.trim()))
      return choices
    else
    {
      List<String> c = new ArrayList<>(choices)
      
      c.remove(var)
      c.add(0, var)
      
      return c
    }
  }
}
