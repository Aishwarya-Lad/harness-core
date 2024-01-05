/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "harness_platform_project" "terraform_project" {
	depends_on = [
		harness_platform_organization.terraform_org
	]
	identifier = "terraform_project"
	name = "terraform_project"
	org_id = harness_platform_organization.terraform_org.id
	color = "#472848"
}
