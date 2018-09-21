package org.symphonyoss.s2.fugue.cicd.v1

class EnvironmentTypeConfig implements Serializable
{
  
  private def     steps_
  
  public EnvironmentTypeConfig(steps, String environmentType)
  {
    steps_ = steps;
    
    steps_.sh 'pwd'
    steps_.sh 'ls'
    steps_.sh 'ls config/environment/' + environmentType
    def config = steps_.readJSON file:'config/environment/' + environmentType + '/environmentType.json'
    
    steps_.echo 'config=' + config
  }
}