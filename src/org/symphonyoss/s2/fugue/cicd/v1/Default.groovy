/*
 * Copyright 2018 Symphony Communication Services, LLC.
 *
 * All Rights Reserved
 */

package org.symphonyoss.s2.fugue.cicd.v1

class Default
{
  public static String  value(String var, String defaultValue)
  {
    if(var==null || "".equals(var.trim()))
      return defaultValue
    else
      return var
  }
}
