/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing;

import io.harness.ModuleType;
import io.harness.licensing.interfaces.clients.ModuleLicenseClient;
import io.harness.licensing.interfaces.clients.local.CDLocalClient;
import io.harness.licensing.interfaces.clients.local.CELocalClient;
import io.harness.licensing.interfaces.clients.local.CETLocalClient;
import io.harness.licensing.interfaces.clients.local.CFLocalClient;
import io.harness.licensing.interfaces.clients.local.CILocalClient;
import io.harness.licensing.interfaces.clients.local.ChaosLocalClient;
import io.harness.licensing.interfaces.clients.local.CodeLocalClient;
import io.harness.licensing.interfaces.clients.local.IACMLocalClient;
import io.harness.licensing.interfaces.clients.local.IDPLocalClient;
import io.harness.licensing.interfaces.clients.local.SEILocalClient;
import io.harness.licensing.interfaces.clients.local.SRMLocalClient;
import io.harness.licensing.interfaces.clients.local.STOLocalClient;
import io.harness.licensing.mappers.LicenseObjectMapper;
import io.harness.licensing.mappers.modules.CDLicenseObjectMapper;
import io.harness.licensing.mappers.modules.CELicenseObjectMapper;
import io.harness.licensing.mappers.modules.CETLicenseObjectMapper;
import io.harness.licensing.mappers.modules.CFLicenseObjectMapper;
import io.harness.licensing.mappers.modules.CILicenseObjectMapper;
import io.harness.licensing.mappers.modules.ChaosLicenseObjectMapper;
import io.harness.licensing.mappers.modules.CodeLicenseObjectMapper;
import io.harness.licensing.mappers.modules.IACMLicenseObjectMapper;
import io.harness.licensing.mappers.modules.IDPLicenseObjectMapper;
import io.harness.licensing.mappers.modules.SEILicenseObjectMapper;
import io.harness.licensing.mappers.modules.SRMLicenseObjectMapper;
import io.harness.licensing.mappers.modules.STOLicenseObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ModuleLicenseRegistrarFactory {
  private static Map<ModuleType, ModuleLicenseRegistrar> registrar = new HashMap<>();

  private ModuleLicenseRegistrarFactory() {}

  static {
    registrar.put(
        ModuleType.CD, new ModuleLicenseRegistrar(ModuleType.CD, CDLicenseObjectMapper.class, CDLocalClient.class));
    registrar.put(
        ModuleType.CI, new ModuleLicenseRegistrar(ModuleType.CI, CILicenseObjectMapper.class, CILocalClient.class));
    registrar.put(
        ModuleType.CE, new ModuleLicenseRegistrar(ModuleType.CE, CELicenseObjectMapper.class, CELocalClient.class));
    registrar.put(
        ModuleType.CV, new ModuleLicenseRegistrar(ModuleType.CV, SRMLicenseObjectMapper.class, SRMLocalClient.class));
    registrar.put(
        ModuleType.SRM, new ModuleLicenseRegistrar(ModuleType.SRM, SRMLicenseObjectMapper.class, SRMLocalClient.class));
    registrar.put(
        ModuleType.CF, new ModuleLicenseRegistrar(ModuleType.CF, CFLicenseObjectMapper.class, CFLocalClient.class));
    registrar.put(
        ModuleType.STO, new ModuleLicenseRegistrar(ModuleType.STO, STOLicenseObjectMapper.class, STOLocalClient.class));
    registrar.put(ModuleType.CHAOS,
        new ModuleLicenseRegistrar(ModuleType.CHAOS, ChaosLicenseObjectMapper.class, ChaosLocalClient.class));
    registrar.put(ModuleType.IACM,
        new ModuleLicenseRegistrar(ModuleType.IACM, IACMLicenseObjectMapper.class, IACMLocalClient.class));
    registrar.put(
        ModuleType.CET, new ModuleLicenseRegistrar(ModuleType.CET, CETLicenseObjectMapper.class, CETLocalClient.class));
    registrar.put(
        ModuleType.SEI, new ModuleLicenseRegistrar(ModuleType.SEI, SEILicenseObjectMapper.class, SEILocalClient.class));
    registrar.put(
        ModuleType.IDP, new ModuleLicenseRegistrar(ModuleType.IDP, IDPLicenseObjectMapper.class, IDPLocalClient.class));
    registrar.put(ModuleType.CODE,
        new ModuleLicenseRegistrar(ModuleType.CODE, CodeLicenseObjectMapper.class, CodeLocalClient.class));
  }

  public static Class<? extends LicenseObjectMapper> getLicenseObjectMapper(ModuleType moduleType) {
    return registrar.get(moduleType).getObjectMapper();
  }

  public static Class<? extends ModuleLicenseClient> getModuleLicenseClient(ModuleType moduleType) {
    return registrar.get(moduleType).getModuleLicenseClient();
  }

  public static Set<ModuleType> getSupportedModuleTypes() {
    return registrar.keySet();
  }
}
