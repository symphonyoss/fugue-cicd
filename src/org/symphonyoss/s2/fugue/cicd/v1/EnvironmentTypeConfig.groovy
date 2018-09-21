package org.symphonyoss.s2.fugue.cicd.v1

class EnvironmentTypeConfig implements Serializable
{
  
  private def steps_
  String      accountId_
  String      vpcId_
  String      loadBalancerCertificateArn_
  String[]    loadBalancerSecurityGroups_
  String[]    loadBalancerSubnets_
  
  public EnvironmentTypeConfig(steps, amazon)
  {
    steps_ = steps;
    accountId_ = amazon."accountId"
    vpcId_ = amazon."vpcId"
    loadBalancerCertificateArn_ = amazon."loadBalancerCertificateArn"
    loadBalancerSecurityGroups_ = amazon."loadBalancerSecurityGroups"
    loadBalancerSubnets_ = amazon."loadBalancerSubnets"
  }
  
  public String[] getLoadBalancerSecurityGroups()
  {
    return loadBalancerSecurityGroups_
  }
}