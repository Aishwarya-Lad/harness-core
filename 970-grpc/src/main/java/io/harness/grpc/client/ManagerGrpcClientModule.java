/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc.client;

import io.harness.exception.KeyManagerBuilderException;
import io.harness.exception.TrustManagerBuilderException;
import io.harness.govern.ProviderModule;
import io.harness.grpc.auth.DelegateAuthCallCredentials;
import io.harness.security.TokenGenerator;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509TrustManagerBuilder;
import io.harness.version.VersionInfo;
import io.harness.version.VersionInfoManager;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.internal.GrpcUtil;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import javax.net.ssl.SSLException;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Defines plumbing to connect to manager via grpc.
 */
@Slf4j
public class ManagerGrpcClientModule extends ProviderModule {
  private static ManagerGrpcClientModule instance;
  private final String deployMode = System.getenv().get("DEPLOY_MODE");

  public static ManagerGrpcClientModule getInstance() {
    if (instance == null) {
      instance = new ManagerGrpcClientModule();
    }
    return instance;
  }

  @Provides
  @Singleton
  CallCredentials callCredentials(
      Config config, @Named("Application") String application, TokenGenerator tokenGenerator) {
    boolean isSsl = isSsl(config, application);
    if (!isSsl) {
      return new DelegateAuthCallCredentials(tokenGenerator, config.accountId, false);
    }
    return new DelegateAuthCallCredentials(tokenGenerator, config.accountId, true);
  }

  @Named("manager-channel")
  @Singleton
  @Provides
  public Channel managerChannel(
      Config config, @Named("Application") String application, VersionInfoManager versionInfoManager)
      throws SSLException, TrustManagerBuilderException, KeyManagerBuilderException {
    String authorityToUse = computeAuthority(config, versionInfoManager.getVersionInfo());
    boolean isSsl = isSsl(config, application);

    if (!isSsl) {
      return NettyChannelBuilder.forTarget(config.target).overrideAuthority(authorityToUse).usePlaintext().build();
    }

    X509TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
    SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient().trustManager(trustManager);

    if (StringUtils.isNotEmpty(config.clientCertificateFilePath)
        && StringUtils.isNotEmpty(config.clientCertificateKeyFilePath)) {
      X509KeyManager keyManager =
          new X509KeyManagerBuilder()
              .withClientCertificateFromFile(config.clientCertificateFilePath, config.clientCertificateKeyFilePath)
              .build();
      sslContextBuilder.keyManager(keyManager);
    }

    SslContext sslContext = sslContextBuilder.build();
    return NettyChannelBuilder.forTarget(config.target)
        .overrideAuthority(authorityToUse)
        .sslContext(sslContext)
        .intercept(HarnessRoutingGrpcInterceptor.MANAGER)
        .build();
  }

  private boolean isSsl(Config config, @Named("Application") String application) {
    if ("KUBERNETES_ONPREM".equals(deployMode)) {
      return ("Delegate".equalsIgnoreCase(application)) && ("https".equalsIgnoreCase(config.scheme));
    }
    return true;
  }

  private String computeAuthority(Config config, VersionInfo versionInfo) {
    log.info("The GRPC config is: {}", config);
    String defaultAuthority = "default-authority.harness.io";
    String authorityToUse;
    if (!isValidAuthority(config.authority)) {
      log.info("Authority in config {} is invalid. Using default value {}", config.authority, defaultAuthority);
      authorityToUse = defaultAuthority;
    } else {
      log.info("Deploy Mode is {}. Using non-versioned authority", deployMode);
      authorityToUse = config.authority;
    }
    return authorityToUse;
  }

  private static boolean isValidAuthority(String authority) {
    try {
      GrpcUtil.checkAuthority(authority);
    } catch (Exception ignore) {
      log.error("Exception occurred when checking for valid authority", ignore);
      return false;
    }
    return true;
  }

  @Value
  @Builder
  public static class Config {
    String target;
    String authority;
    String accountId;
    @ToString.Exclude String accountSecret;
    String scheme;
    String clientCertificateFilePath;
    String clientCertificateKeyFilePath;

    // As of now ignored (always trusts all certs)
    boolean trustAllCertificates;
  }
}
