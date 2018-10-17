package org.symphonyoss.s2.fugue.cicd.v1

class EnvironmentTypeConfig implements Serializable
{
  private String      accountId_
  private String      vpcId_
  private String      loadBalancerCertificateArn_
  private String[]    loadBalancerSecurityGroups_
  private String[]    loadBalancerSubnets_
  private String      clusterId_
  
  public EnvironmentTypeConfig(amazon)
  {
    accountId_ = amazon."accountId"
    vpcId_ = amazon."vpcId"
    loadBalancerCertificateArn_ = amazon."loadBalancerCertificateArn"
    loadBalancerSecurityGroups_ = amazon."loadBalancerSecurityGroups"
    loadBalancerSubnets_ = amazon."loadBalancerSubnets"
    clusterId_ = amazon."ecsCluster"
  }
  
  public String getAccountId()
  {
    return accountId_;
  }

  public String getVpcId()
  {
    return vpcId_;
  }

  public String getLoadBalancerCertificateArn()
  {
    return loadBalancerCertificateArn_;
  }

  public String[] getLoadBalancerSubnets()
  {
    return loadBalancerSubnets_;
  }

  public String getClusterId()
  {
    return clusterId_;
  }

  public String[] getLoadBalancerSecurityGroups()
  {
    return loadBalancerSecurityGroups_
  }
}