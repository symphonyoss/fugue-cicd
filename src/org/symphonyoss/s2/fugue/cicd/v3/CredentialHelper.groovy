package org.symphonyoss.s2.fugue.cicd.v3

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;

class CredentialHelper implements Serializable
{
    public static void saveCredential(steps, String id, String accessKeyId, String secretKey)
    {
      Credentials myawscreds = (Credentials) new AWSCredentialsImpl(
        CredentialsScope.GLOBAL, id, accessKeyId, secretKey, accessKeyId)
      
      def store = SystemCredentialsProvider.getInstance().getStore();
      
      if(store.addCredentials(Domain.global(), myawscreds))
      {
        steps.echo 'Created credential ' + id
      }
      else
      {
        Credentials existingCred = null
        
        store.getCredentials(Domain.global()).each
        {
          e ->
            if(id.equals(e.id))
            {
              existingCred = e
            }
        }
        
        if(existingCred == null)
        {
          throw new IllegalStateException("Can't create or find credential " + id)
        }
        
        if(store.updateCredentials(Domain.global(), existingCred, myawscreds))
        {
          return
        }
        else
        {
          throw new IllegalStateException("Can't create or update credential " + id)
        }
      }
    }
}