# Verified execution on 3 November 2026

## Build and deployment

- Jenkins job: HabitApp-GitOps-CI. Build 3 completed full CI; build 4 was automatically started by an SCM change and also succeeded.
- Build 4 source commit: 2da366c2e6ad87d80b3b6f25b1d77b0521ce17fd. Published image: `985344363717.dkr.ecr.ap-south-1.amazonaws.com/habitapp:4-2da366c2e6ad`.
- All stages passed: checkout, Maven build, seven tests and packaging, SonarQube, Quality Gate, Docker, Helm validation, image publication, Helm values update and deployment commit.
- SonarQube: Passed, 82.5% coverage, zero open security/reliability/maintainability issues and 0.0% duplication. The original wildcard CORS finding was corrected; the new preflight rejection test passes.
- ECR version history and immutable tag configuration were verified for the HabitApp repository. Raw ECR metadata is saved separately; no claim of vulnerability-free dependencies is made.
- Argo CD reports source type Helm and reached Synced/Healthy. Two application replicas are Ready with zero restarts, resource bounds, probes and ALB target readiness gates.
- Public frontend, readiness and API habit creation/completion/readback checks passed. `/actuator/info` returns image version `4-2da366c2e6ad`.
- The same versioned image also ran locally as UID/GID 10001 with a read-only root, bounded /tmp, dropped capabilities and no privilege escalation. The local dashboard screenshot is labeled as container evidence, not a public browser capture.

## GitOps rollback

1. Jenkins deployment commit 40b07f0 selected release 4; Argo CD deployed it automatically.
2. Test commit 078fb35 selected `unavailable-rollback-check`, an image that does not exist.
3. The new pod reported ErrImagePull/ImagePullBackOff. Argo CD showed Synced/Progressing and two previous replicas remained available.
4. Git revert 122b391 restored the working tag. Argo CD returned to Synced/Healthy, the failed pod disappeared and both working replicas remained Ready.

Timestamped foreground observations:

| UTC time | Observation | Homepage | Readiness |
|---|---|---|---|
| 13:58:48 | Before release 4 | 200 | UP |
| 14:02:05 | Old and new replicas overlapped | 200 | UP |
| 14:09:36 | Unavailable-image rollout | 200 | UP |
| 14:13:49 | After Git rollback | 200 | UP |

These four observations do not establish uninterrupted availability between samples. The attempted continuous background probe was not started because automatic approval review blocked that action. The retained CSV contains the actual foreground observations only.

## Monitoring and security

Populated Grafana CPU, memory, restart and pod-state panels were captured. Kubernetes application logs, events and resource usage were exported. Argo CD history records the automated deploy, failed image configuration and Git-based recovery.

Argo controller RBAC checks: patch deployments in habitapp yes; patch deployments in blogapp no; create cluster roles no. CI AWS permissions cover only the HabitApp ECR repository and authorization token. The Git SSH deploy key is repository-scoped; the Sonar token is project-scoped. A tracked-file credential-pattern check found zero matches. This check is not a comprehensive security audit.

Initial setup failures were corrected: Maven cache ownership, the CI key's unintended passphrase, and the Argo namespace cache's read permissions. Helm release-metadata writes for the Argo control plane timed out, so its pinned chart-rendered resources were applied administratively. The application Helm deployment is managed by Argo CD and is verified healthy.

## Limits and evidence

The upstream Spring Boot 3.3.4 application is an unauthenticated in-memory demonstration. Data is not replicated between pods and is lost on restart; session affinity does not change that limitation. The public URL is HTTP. Dependency maintenance remains necessary before production use.

Chrome automation blocked the public HTTP address even though live API checks succeeded. The application dashboard screenshot therefore shows the versioned local container; cloud execution is established separately by public endpoint outputs, ECR metadata, Argo CD and Kubernetes evidence.

Source, CI configuration and operating files are in GitHub. Screenshots, full Jenkins output, seven-test results, JaCoCo XML, ECR metadata, rollback records, endpoint observations and logs are preserved under `C:\Users\Soundar\Desktop\Project-Evidence\HabitApp`.
