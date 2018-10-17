package org.symphonyoss.s2.fugue.cicd.v2

class Station implements Serializable {
  String                name
  String                region
  String                realm
  String                environment
  boolean               primaryEnvironment
  boolean               primaryRegion
  String                environmentType
  String                logGroupName
  List<String>          tenants = new ArrayList<>()

  public String toString() {
    "    {" +
        "\n      name               =" + name +
        "\n      region             =" + region +
        "\n      realm              =" + realm +
        "\n      environment        =" + environment +
        "\n      environmentType    =" + environmentType +
        "\n      logGroupName       =" + logGroupName +
        "\n      tenants            =" + tenants +
        "\n      primaryEnvironment =" + primaryEnvironment +
        "\n      primaryRegion      =" + primaryRegion +
        "\n    }"
  }

  public Station withName(String n) {
    this.name = n
    return this
  }

  public Station withRegion(String n) {
    region = n
    return this
  }

  public Station withRealm(String n) {
    realm = n
    return this
  }

  public Station withEnvironment(String n) {
    environment = n
    return this
  }
  
  public Station withPrimaryEnvironment(Boolean n) {
    primaryEnvironment = n==null ? false : n
    return this
  }
  
  public Station withPrimaryRegion(Boolean n) {
    primaryRegion = n==null ? false : n
    return this
  }

  /**
   * Override the calculated log group name with the given value.
   * 
   * Normal services SHOULD NOT CALL THIS METHOD.
   * 
   * @param n The log group name override.
   * 
   * @return this (fluent method)
   */
  public Station withLogGroupName(String n) {
    logGroupName = n
    return this
  }

  public Station withEnvironmentType(String n) {
    environmentType = n
    return this
  }

  public Station withTenants(String ...t) {
    for(String tenant : t)
      tenants.add(tenant)
    return this
  }
}
