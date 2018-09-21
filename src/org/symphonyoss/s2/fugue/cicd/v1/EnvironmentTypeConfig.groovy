package org.symphonyoss.s2.fugue.cicd.v1

class EnvironmentTypeConfig implements Serializable
{
  
  private def     steps_
  
  public EnvironmentTypeConfig(steps, String environmentType)
  {
    steps_ = steps;
    
    steps.echo 'TT1'
    steps.echo 'environmentType=' + environmentType
    steps_.sh 'pwd'
    steps.echo 'TT2'
    steps_.sh 'ls'
    steps.echo 'TT3'
    steps_.sh 'ls config/environment/' + environmentType
    steps.echo 'TT4'
    def config = steps_.readJSON file:'config/environment/' + environmentType + '/environmentType.json'
    
    steps_.echo 'config=' + config
  }
}