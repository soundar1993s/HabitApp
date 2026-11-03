# HabitApp CI and GitOps deployment

## Current scope

The supplied Spring Boot Java 21 application is an in-memory demonstration. Each replica keeps its own data; data is lost on restart and is not shared between replicas. Do not present it as a persistent, multi-user production service. The deployment demonstrates the requested CI and GitOps workflow. Public exposure should be limited to demonstration data because the upstream API has no authentication.

The original Spring Boot 3.3.4 framework version is retained. Dependency maintenance and a vulnerability review remain necessary before production use.

## CI setup

1. Build the extended agent: `docker build -f ci/agent/Dockerfile -t habitapp-jenkins-agent:local .`. It extends the existing BlogReact agent and adds Maven and Helm. Use this image when recreating the existing inbound build-agent container with its existing environment file, network, socket group and volumes. Never display its secret.
2. Push this source to your own GitHub HabitApp repository. The Argo CD manifests currently expect `https://github.com/soundar1993s/HabitApp.git`, branch `main`.
3. In SonarQube create project key `habit-tracker`. Generate a project analysis token; store it as Jenkins Secret text `habitapp-sonar-token`. Configure the signed project webhook to `http://blogapp-jenkins:8080/sonarqube-webhook/`, using the same signing secret selected on Jenkins' `sonarqube-server` configuration. Never publish the token or secret.
4. Store Docker Hub username and access token in Jenkins Username with password credential `habitapp-registry`. Store GitHub username and a repository-scoped write token as `habitapp-git`. Grant only the required repository content write permission.
5. Create a Pipeline from SCM, your HabitApp Git URL, branch `*/main`, script `Jenkinsfile`. Set `IMAGE_REPOSITORY` to your actual Docker Hub namespace/habitapp. The namespace must exist and the credential must allow pushes.
6. Build manually first. Capture Maven test output, Sonar quality gate, pipeline success and registry image tag. The pipeline does not run kubectl or Helm install.
7. For automatic builds, configure Jenkins Git plugin's 'Polling ignores commits from certain users' extension to ignore `jenkins@localhost`, then enable Poll SCM `H/5 * * * *`, or configure an authenticated GitHub webhook reachable from GitHub. Do not poll without excluding the CI deployment commits; they would repeatedly trigger new builds. `[skip ci]` alone is not a Jenkins trigger filter.

## Kubernetes and Argo CD

Use an existing supported Kubernetes cluster and install a pinned, reviewed Argo CD release using the official installation documentation. Keep Argo CD's dashboard private; use port forwarding for screenshots. Do not create the application before the first real image tag has been committed to the chart.

An administrator bootstraps these resources once:

```sh
kubectl apply -f k8s/namespace.yaml
kubectl apply -f argocd/project.yaml
kubectl apply -f argocd/application.yaml
```

The AppProject permits only this repository, the habitapp namespace, and the chart's resource kinds. The workload ServiceAccount has no API token or Kubernetes privileges. The chart supports an optional existing Secret without storing its values in Git. No secret is needed by this upstream in-memory application.

The default Service is a LoadBalancer. On a local cluster use `--set service.type=ClusterIP` and port forwarding; that is local evidence, not a public URL. On EKS ensure the appropriate load-balancer controller or supported service integration is installed and the external address is reachable. For Ingress, install a matching ingress controller and configure DNS/TLS separately.

Argo CD renders the Helm chart and manages the resources; it does not create a Helm release. For a separate direct-Helm demonstration, use a temporary namespace, then remove that release before handing the same namespace/resources to Argo CD. Do not make Helm and Argo CD concurrently manage the same release.

## Deployment and rollback evidence

1. Confirm Argo CD shows Synced and Healthy and Kubernetes shows two Ready replicas.
2. Start an external HTTP probe against the application during the update; save response codes and timestamps. Endpoint checks prove HTTP availability, not in-memory data continuity.
3. Make an application change, push it, run Jenkins and verify a new immutable image tag is committed under `helm/habitapp/values.yaml`.
4. Show Argo CD detecting the Git commit and synchronizing. Save old/new image references, rollout status, pod ages and endpoint results.
5. To roll back, revert the deployment-tag commit in Git and push the revert. Argo CD restores the previous image. Do not use `kubectl rollout undo` while Argo CD self-healing is enabled; Git would overwrite it.
6. Record recovery with Argo CD Synced/Healthy, ready replicas and successful endpoint probes.

## Monitoring and logs

```sh
kubectl get pods -n habitapp -o wide
kubectl top pods -n habitapp
kubectl logs -n habitapp -l app.kubernetes.io/name=habitapp --tail=80 --prefix
kubectl get events -n habitapp --sort-by=.metadata.creationTimestamp
kubectl get application habitapp -n argocd
```

`kubectl top` requires metrics-server. Capture pod restarts and resource usage plus Argo CD health. Keep passwords, repository tokens and Secret contents out of screenshots.

## Submission checklist

- Your GitHub repository with Java source, tests, pom.xml, Dockerfile, Jenkinsfile, Helm chart, Argo CD manifests and this runbook.
- Actual Maven/Jenkins success, Sonar analysis and quality gate screenshots.
- Versioned image available in the registry and the same tag in the Git chart and running pods.
- Argo CD Synced/Healthy screenshots and automatic update/rollback evidence.
- Pod status, CPU/memory and application log screenshots.
- A reachable application URL. A localhost address is only usable on this computer.

Prepared configuration is not evidence of a completed deployment. Record only checks actually executed.
