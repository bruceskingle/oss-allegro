/*
 *
 *
 * Copyright 2020 Symphony Communication Services, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.symphony.oss.allegro.api;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symphony.oss.canon.runtime.ModelRegistry;
import com.symphony.oss.canon.runtime.jjwt.Rs512JwtGenerator;
import com.symphony.oss.commons.dom.json.IImmutableJsonDomNode;
import com.symphony.oss.commons.dom.json.ImmutableJsonObject;
import com.symphony.oss.commons.dom.json.jackson.JacksonAdaptor;
import com.symphony.oss.commons.fault.FaultAccumulator;
import com.symphony.oss.models.allegro.canon.facade.AllegroMultiTenantConfiguration;
import com.symphony.oss.models.allegro.canon.facade.IAllegroMultiTenantConfiguration;
import com.symphony.oss.models.core.canon.CoreModel;
import com.symphony.oss.models.core.canon.facade.PodAndUserId;
import com.symphony.oss.models.object.canon.ObjectModel;
import com.symphony.s2.authc.canon.AuthcModel;
import com.symphony.s2.authc.canon.facade.PrincipalCredential;

public class AllegroMultiTenantApi extends AllegroBaseApi implements IAllegroMultiTenantApi
{
  private final PodAndUserId      userId_;
  private final Rs512JwtGenerator jwtBuilder_;
  
  AllegroMultiTenantApi(AbstractBuilder<?, ?> builder)
  {
    super(builder);
    
    userId_ = builder.config_.getUserId();
    
    jwtBuilder_ = new Rs512JwtGenerator(builder.rsaCredential_)
        .withClaim("userId", String.valueOf(userId_))
        ;
    
    if(builder.config_.getKeyId() != null)
      jwtBuilder_.withClaim("kid", builder.config_.getKeyId());
  }
  
  public static class Builder extends AbstractBuilder<Builder, IAllegroMultiTenantApi>
  {
    public Builder()
    {
      super(Builder.class);
    }

    @Override
    protected IAllegroMultiTenantApi construct()
    {
      return new AllegroMultiTenantApi(this);
    }
  }
  
  /**
   * The builder implementation.
   * 
   * This is implemented as an abstract class to allow for sub-classing in future.
   * 
   * Any sub-class of AllegroApi would need to implement its own Abstract sub-class of this class
   * and then a concrete Builder class which is itself a sub-class of that.
   * 
   * @author Bruce Skingle
   *
   * @param <T> The type of the concrete Builder
   * @param <B> The type of the built class, some subclass of AllegroApi
   */
  protected static abstract class AbstractBuilder<T extends AbstractBuilder<T,B>, B extends IAllegroMultiTenantApi>
  extends AllegroBaseApi.AbstractBuilder<IAllegroMultiTenantConfiguration,
    AllegroMultiTenantConfiguration.AbstractAllegroMultiTenantConfigurationBuilder<?, IAllegroMultiTenantConfiguration>, T, B>
  {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    public AbstractBuilder(Class<T> type)
    {
      super(type, new AllegroMultiTenantConfiguration.Builder());
    }

    @Override
    public T withConfiguration(Reader reader)
    {
      withConfiguration(allegroModelRegistry_.parseOne(reader, AllegroMultiTenantConfiguration.TYPE_ID, IAllegroMultiTenantConfiguration.class));
      
      return self();
    }
    
    @Deprecated
    public T withUserId(PodAndUserId userId)
    {
      configBuilder_.withUserId(userId);
      builderSet_ = true;
      
      return self();
    }
    
    @Deprecated
    public T withUserId(long userId)
    {
      configBuilder_.withUserId(userId);
      builderSet_ = true;
      
      return self();
    }
    
    @Deprecated
    public T withKeyId(String keyId)
    {
      configBuilder_.withKeyId(keyId);
      builderSet_ = true;
      
      return self();
    }
    
    @Deprecated
    public T withPrincipalCredentialFile(String principalCredentialFile)
    {
      if(principalCredentialFile == null)
        throw new IllegalArgumentException("Credential is required");
      
      File file = new File(principalCredentialFile);
      
      if(!file.canRead())
        throw new IllegalArgumentException("Credential file \""  + file.getAbsolutePath() + "\" is unreadable");
      
      try
      {
        ModelRegistry modelRegistry = new ModelRegistry()
        .withFactories(ObjectModel.FACTORIES)
        .withFactories(CoreModel.FACTORIES)
        .withFactories(AuthcModel.FACTORIES)
        ;
        
        IImmutableJsonDomNode json = JacksonAdaptor.adapt(MAPPER.readTree(file)).immutify();
        PrincipalCredential principalCredential = new PrincipalCredential((ImmutableJsonObject)json, modelRegistry);
        
        withRsaCredential(principalCredential.getPrivateKey());
        withKeyId(principalCredential.getKeyId().toString());
        withUserId(principalCredential.getUserId());
      }
      catch (IOException e)
      {
        throw new IllegalArgumentException("Unable to read credential file \""  + file.getAbsolutePath() + "\".", e);
      }
      
      return self();
    }

    @Override
    protected void validate(FaultAccumulator faultAccumulator)
    {
      super.validate(faultAccumulator);
      
      if(config_.getRsaPemCredentialFile() == null && config_.getRsaPemCredential() == null)
          faultAccumulator.error("rsaCredential is required");
      
      faultAccumulator.checkNotNull(config_.getUserId(), "User ID");
    }
  }

  @Override
  public PodAndUserId getUserId()
  {
    return userId_;
  }

  @Override
  public String getSessionToken()
  {
    return jwtBuilder_.createJwt();
  }
}
