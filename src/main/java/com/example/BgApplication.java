package com.example;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.operations.routes.MapRouteRequest;
import org.cloudfoundry.operations.routes.UnmapRouteRequest;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.cloudfoundry.reactor.uaa.ReactorUaaClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * For more, see <a href="https://github.com/cloudfoundry/cf-java-client">
 * this article on blue-green deployment</a>.
 *
 * @author Josh Long
 */
@SpringBootApplication
public class BgApplication {

	@Bean
	ReactorCloudFoundryClient cloudFoundryClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
		return ReactorCloudFoundryClient.builder()
				.connectionContext(connectionContext)
				.tokenProvider(tokenProvider)
				.build();
	}

	@Bean
	ReactorDopplerClient dopplerClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
		return ReactorDopplerClient.builder()
				.connectionContext(connectionContext)
				.tokenProvider(tokenProvider)
				.build();
	}

	@Bean
	ReactorUaaClient uaaClient(ConnectionContext connectionContext, TokenProvider tokenProvider) {
		return ReactorUaaClient.builder()
				.connectionContext(connectionContext)
				.tokenProvider(tokenProvider)
				.build();
	}

	@Bean
	DefaultConnectionContext connectionContext(@Value("${cf.api}") String apiHost) {
		if (apiHost.contains("://")) {
			apiHost = apiHost.split("://")[1];
		}
		return DefaultConnectionContext.builder()
				.apiHost(apiHost)
				.build();
	}

	@Bean
	PasswordGrantTokenProvider tokenProvider(@Value("${cf.user}") String username,
	                                         @Value("${cf.password}") String password) {
		return PasswordGrantTokenProvider.builder()
				.password(password)
				.username(username)
				.build();
	}

	@Bean
	DefaultCloudFoundryOperations cloudFoundryOperations(
			CloudFoundryClient cloudFoundryClient,
			ReactorDopplerClient dopplerClient,
			ReactorUaaClient uaaClient,
			@Value("${cf.org}") String organization,
			@Value("${cf.space}") String space) {
		return DefaultCloudFoundryOperations.builder()
				.cloudFoundryClient(cloudFoundryClient)
				.dopplerClient(dopplerClient)
				.uaaClient(uaaClient)
				.organization(organization)
				.space(space)
				.build();
	}

	@Bean
	ApplicationRunner applicationRunner(
			@Value("${bg.appName:cdlive}") String applicationName, Deployer deployer) {
		return args -> {
			deployer.deploy(applicationName);
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(BgApplication.class, args);
	}


}

@RestController
class WebhookDeployer {

	private final Deployer deployer;

	@PostMapping("/deploy")
	public void deploy(@RequestBody Map<String, String> body) {
		String packageName = body.get("package");
		this.deployer.deploy(packageName);
	}

	public WebhookDeployer(Deployer deployer) {
		this.deployer = deployer;
	}
}


@Component
class Deployer {

	private final CloudFoundryOperations cloudFoundryClient;

	public Deployer(CloudFoundryOperations cloudFoundryClient) {
		this.cloudFoundryClient = cloudFoundryClient;
	}

	public void deploy(String appName) {

		String live = appName + "-live", staging = appName + "-staging";

		this.cloudFoundryClient.routes()
				.unmap(UnmapRouteRequest.builder()
						.applicationName(appName)
						.domain("cfapps.io")
						.host(staging)
						.build())
				.block();

		this.cloudFoundryClient.routes()
				.map(MapRouteRequest.builder()
						.applicationName(appName)
						.domain("cfapps.io")
						.host(live)
						.build())
				.block();
	}
}