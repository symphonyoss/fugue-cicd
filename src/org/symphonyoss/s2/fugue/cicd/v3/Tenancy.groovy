package org.symphonyoss.s2.fugue.cicd.v3

enum Tenancy {
  SINGLE, MULTI;
  
  public static ContainerType parse(String s)
  {
    if(s==null)
      return MULTI;
      
    for(ContainerType t : values())
      if(s.equalsIgnoreCase(t.toString()))
        return t;
        
    throw new IllegalArgumentException("\"${s}\" is not a valid Tenancy");
  }
}
