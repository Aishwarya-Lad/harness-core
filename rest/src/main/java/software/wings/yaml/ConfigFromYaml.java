package software.wings.yaml;

import software.wings.beans.Application;

import java.util.List;

public class ConfigFromYaml {
  @YamlSerialize private List<Application> applications;
  @YamlSerialize private String account;

  public List<Application> getApplications() {
    return applications;
  }

  public void setApplications(List<Application> applications) {
    this.applications = applications;
  }

  public String getAccount() {
    return account;
  }

  public void setAccount(String account) {
    this.account = account;
  }
}
