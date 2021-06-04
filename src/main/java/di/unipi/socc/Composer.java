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

    private static String ELK_YAML = "configs/elk.yml";
    public static void main(String[] args) {
        // Parse arguments
        String inputFile = args[0];
        InputStream inputStream = null;;
        try {
            inputStream = new FileInputStream(new File(inputFile));
        } catch (FileNotFoundException e) {
            System.err.println("ERROR: Input file not found");
            return;
        } 

        String outputFile = args[1];
        FileWriter writer = null;;
        try {
            writer = new FileWriter(new File(outputFile));
        } catch (IOException e) {
            System.err.println("ERROR: Output file is a directory");
            return;
        }

        // Parse input file
        Yaml yaml = new Yaml();
        Map<String,Object> spec = yaml.load(inputStream); 

        // Add Docker Compose Version
        spec.put("version", "3.8");

        // Get services (and create empty maps for services "just named")
        Map<String,Object> services = (Map<String,Object>) spec.get("services");
        for(String serviceName : services.keySet())
            if(services.get(serviceName) == null) 
                services.put(serviceName,new HashMap<String,Object>());

        // Complete services
        for(String serviceName : services.keySet()) {
            Map<String,Object> service = (Map<String,Object>) services.get(serviceName);
            
            // Add Docker image
            service.put("image",DOCKER_IMAGE);

            // Get environment
            if(service.get("environment") == null) 
                service.put("environment", new HashMap<String,Object>());
            Map<String,Object> environment = (Map<String,Object>) service.get("environment");
            
            // Set TIMEOUT, PICK_PERCENTAGE, and FAIL_PERCENTAGE (if not there)
            if(environment.get("TIMEOUT") == null)
                environment.put("TIMEOUT", DEFAULT_TIMEOUT);
            if(environment.get("PICK_PERCENTAGE") == null)
                environment.put("PICK_PERCENTAGE", DEFAULT_PICK_PERCENTAGE);
            if(environment.get("FAIL_PERCENTAGE") == null)
                environment.put("FAIL_PERCENTAGE", DEFAULT_FAIL_PERCENTAGE);
            
            // Set BACKEND_SERVICES
            if(service.containsKey("depends_on")) {
                List<String> backendServices = (List<String>) service.get("depends_on");
                StringBuilder buffer = new StringBuilder();
                buffer.append(backendServices.get(0));
                for(int i=1; i<backendServices.size(); i++) {
                    buffer.append(":");
                    buffer.append(backendServices.get(i));
                }
                environment.put("BACKEND_SERVICES", buffer.toString());
            }

            // Update port mappings (if any)
            if(service.containsKey("ports")) {
                List<Object> ports = (List<Object>) service.get("ports");
                List<String> portMappings = new ArrayList<String>();
                for(Object port : ports) {
                    portMappings.add(port.toString() + ":80");
                }
                service.put("ports", portMappings);
            }

            // Add GELF logging
            Map<String,Object> logging = new HashMap<String,Object>();
            logging.put("driver", "gelf");
            Map<String,Object> loggingOptions = new HashMap<String,Object>();
            loggingOptions.put("gelf-address","udp://localhost:12201");
            loggingOptions.put("tag",serviceName);
            logging.put("options", loggingOptions);
        }

        // Parse ELK YAML file...
        InputStream elkYaml = null;
        try {
            elkYaml = new FileInputStream(new File(ELK_YAML));
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        }
        Map<String,Object> elk = yaml.load(elkYaml);
        // ...and add ELK services
        Map<String,Object> elkServices = (Map<String,Object>) elk.get("services"); 
        for(String serviceName : elkServices.keySet())
            services.put(serviceName, elkServices.get(serviceName));

        // Output Docker Compose file
        yaml.dump(spec, writer);
    }
}
