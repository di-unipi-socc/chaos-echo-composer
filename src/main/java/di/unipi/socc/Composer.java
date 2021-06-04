package di.unipi.socc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public final class Composer {

    private static String DOCKER_IMAGE = "diunipisocc/chaosecho:1";

    private static int DEFAULT_TIMEOUT = 10000;
    private static int DEFAULT_PICK_PERCENTAGE = 50;
    private static int DEFAULT_FAIL_PERCENTAGE = 5;

    private static int DEFAULT_REPLICAS = 1;

    private static String ELK_YAML = "configs/elk.yml";

    public static void main(String[] args) {
        // Parse arguments
        String inputFile = args[0];
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File(inputFile));
        } catch (FileNotFoundException e) {
            System.err.println("ERROR: Input file not found");
            return;
        }

        String outputFile = args[1];
        FileWriter writer = null;
        try {
            writer = new FileWriter(new File(outputFile));
        } catch (IOException e) {
            System.err.println("ERROR: Output file is a directory");
            return;
        }

        // Parse input file and get "services"
        Yaml yaml = new Yaml();
        Map<String, Object> spec = yaml.load(inputStream);
        Map<String, Object> services = (Map<String, Object>) spec.get("services");
        for (String serviceName : services.keySet())
            if (services.get(serviceName) == null)
                services.put(serviceName, new HashMap<String, Object>());

        // Parse ELK file and get "elkServices"
        InputStream elkStream = null;
        try {
            elkStream = new FileInputStream(new File(ELK_YAML));
        } catch (FileNotFoundException e) {
            System.err.println("ERROR: Input file not found");
            return;
        }
        Map<String, Object> elk = yaml.load(elkStream);
        Map<String, Object> elkServices = (Map<String, Object>) elk.get("services");

        // Add Docker Compose Version
        spec.put("version", "3.8");

        // Complete services
        for (String serviceName : services.keySet()) {
            Map<String, Object> service = (Map<String, Object>) services.get(serviceName);

            // Add Docker image
            service.put("image", DOCKER_IMAGE);

            // Get environment
            Map<String, Object> environment = (Map<String, Object>) service.get("environment");
            if (environment == null) {
                environment = new HashMap<String, Object>();
                service.put("environment", environment);
            }

            // Set TIMEOUT, PICK_PERCENTAGE, and FAIL_PERCENTAGE (if not there)
            if (environment.get("TIMEOUT") == null)
                environment.put("TIMEOUT", DEFAULT_TIMEOUT);
            if (environment.get("PICK_PERCENTAGE") == null)
                environment.put("PICK_PERCENTAGE", DEFAULT_PICK_PERCENTAGE);
            if (environment.get("FAIL_PERCENTAGE") == null)
                environment.put("FAIL_PERCENTAGE", DEFAULT_FAIL_PERCENTAGE);

            // Set BACKEND_SERVICES
            List<String> dependsOn = (List<String>) service.get("depends_on");
            if (dependsOn != null) {
                StringBuilder buffer = new StringBuilder();
                buffer.append(dependsOn.get(0));
                for (int i = 1; i < dependsOn.size(); i++) {
                    buffer.append(":");
                    buffer.append(dependsOn.get(i));
                }
                environment.put("BACKEND_SERVICES", buffer.toString());
            }

            // Update depends_on
            if (dependsOn == null)
                dependsOn = new ArrayList<String>();
            for (String elkService : elkServices.keySet())
                dependsOn.add(elkService);
            service.put("depends_on", dependsOn);

            // Add deploy-replicas
            Map<String, Object> deploy = (Map<String, Object>) service.get("deploy");
            if (deploy == null) {
                deploy = new HashMap<String, Object>();
                service.put("deploy", deploy);
            }
            if (deploy.get("replicas") == null)
                deploy.put("replicas", DEFAULT_REPLICAS);

            // Update port mappings (if any)
            if (service.containsKey("ports")) {
                List<Object> ports = (List<Object>) service.get("ports");
                List<String> portMappings = new ArrayList<String>();
                for (Object port : ports) {
                    portMappings.add(port.toString() + ":80");
                }
                service.put("ports", portMappings);
            }

            // Add GELF logging
            Map<String, Object> logging = new HashMap<String, Object>();
            logging.put("driver", "gelf");
            Map<String, Object> loggingOptions = new HashMap<String, Object>();
            loggingOptions.put("gelf-address", "udp://localhost:12201");
            loggingOptions.put("tag", serviceName);
            logging.put("options", loggingOptions);
            service.put("logging",logging);
        }

        // Add ELK services
        for (String serviceName : elkServices.keySet())
            services.put(serviceName, elkServices.get(serviceName));

        // Output Docker Compose file
        yaml.dump(spec, writer);
    }
}
