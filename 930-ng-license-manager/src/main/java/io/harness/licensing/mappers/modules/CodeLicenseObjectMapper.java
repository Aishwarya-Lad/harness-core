/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.beans.modules.CodeModuleLicenseDTO;
import io.harness.licensing.entities.modules.CodeModuleLicense;
import io.harness.licensing.helpers.ModuleLicenseHelper;
import io.harness.licensing.mappers.LicenseObjectMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.util.DataSize;

@OwnedBy(HarnessTeam.CODE)
@Singleton
public class CodeLicenseObjectMapper implements LicenseObjectMapper<CodeModuleLicense, CodeModuleLicenseDTO> {
  @Inject private ModuleLicenseHelper moduleLicenseHelper;
  private static final String GIBSuffix = "GiB";

  @Override
  public CodeModuleLicenseDTO toDTO(CodeModuleLicense moduleLicense) {
    return CodeModuleLicenseDTO.builder()
        .numberOfDevelopers(moduleLicense.getNumberOfDevelopers())
        .numberOfRepositories(moduleLicense.getNumberOfRepositories())
        .maxRepoSizeString(toGibiByteString(moduleLicense.getMaxRepoSizeInBytes()))
        .maxRepoSizeInBytes(moduleLicense.getMaxRepoSizeInBytes())
        .build();
  }

  @Override
  public CodeModuleLicense toEntity(CodeModuleLicenseDTO codeModuleLicenseDTO) {
    validateModuleLicenseDTO(codeModuleLicenseDTO);

    return CodeModuleLicense.builder()
        .numberOfDevelopers(codeModuleLicenseDTO.getNumberOfDevelopers())
        .maxRepoSizeInBytes(DataSize.parse(codeModuleLicenseDTO.getMaxRepoSizeString()).toBytes())
        .numberOfRepositories(codeModuleLicenseDTO.getNumberOfRepositories())
        .build();
  }

  @Override
  public void validateModuleLicenseDTO(CodeModuleLicenseDTO codeModuleLicenseDTO) {
    if (!moduleLicenseHelper.isDeveloperLicensingFeatureEnabled(codeModuleLicenseDTO.getAccountIdentifier())) {
      if (codeModuleLicenseDTO.getDeveloperLicenseCount() != null) {
        throw new InvalidRequestException("New Developer Licensing feature is not enabled for this account!");
      }
    }

    if (codeModuleLicenseDTO.getMaxRepoSizeString().isEmpty()) {
      throw new InvalidRequestException("Max repo size in string cannot be empty");
    }

    if (codeModuleLicenseDTO.getDeveloperLicenseCount() != null
        && codeModuleLicenseDTO.getNumberOfDevelopers() == null) {
      // TODO: fetch mapping ratio from DeveloperMapping collection, once that work is complete
      Integer mappingRatio = 1;
      codeModuleLicenseDTO.setNumberOfDevelopers(mappingRatio * codeModuleLicenseDTO.getDeveloperLicenseCount());
    }
  }

  public String toGibiByteString(long bytes) {
    long gibibytes = DataSize.bytes(bytes).toGibibytes();
    return gibibytes + GIBSuffix;
  }
}
