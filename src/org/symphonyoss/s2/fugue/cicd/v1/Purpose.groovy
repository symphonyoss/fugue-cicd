package org.symphonyoss.s2.fugue.cicd.v1

enum Purpose {
  None(0), SmokeTest(1), Service(2);
  
  private final int type_;
  
  private Purpose(int type)
  {
    type_ = type;
  }
  
  public boolean  isValidFor(Purpose other)
  {
    return type_ >= other.type_
  }
}
