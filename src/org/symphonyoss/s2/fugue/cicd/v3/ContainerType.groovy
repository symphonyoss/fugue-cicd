package org.symphonyoss.s2.fugue.cicd.v3

enum ContainerType {
  SERVICE, INIT, SCHEDULED, LAMBDA, EXTERNAL_LAMBDA, EXTERNAL_HTTP;
  
  public static ContainerType parse(String s)
  {
    if(s==null)
      return SERVICE;
      
    for(ContainerType t : values())
      if(s.equalsIgnoreCase(t.toString()))
        return t;
        
    throw new IllegalArgumentException("\"${s}\" is not a valid ContainerType");
  }
}
