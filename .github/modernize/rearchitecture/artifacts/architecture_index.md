# Architecture Index

## Implementation Guide

This index is not the full contract. Do not implement from this file alone; follow the artifact paths below.

### Global artifacts

- `unit_graph.yaml`
  - Who reads: all implementation workers.
  - Use for: unit boundaries, source anchors, startup trigger, and Spring-discovered entrypoints.
  - How to consume: find the assigned unit and follow its source anchors and dynamic entrypoints.
- `wire_contracts.yaml`
  - Who reads: workers changing external interfaces.
  - Use for: outward HTTP, messaging, and other integration contracts.
  - How to consume: filter contracts by the assigned unit; currently no outward contract is declared in source.
- `shared_modules.yaml`
  - Who reads: workers changing shared code.
  - Use for: modules referenced by more than one unit.
  - How to consume: filter rows where `used_by_units` includes the assigned unit; currently none are recorded.
- `cross_unit_state.yaml`
  - Who reads: workers changing implicit shared state.
  - Use for: state flows between units.
  - How to consume: filter flows where `writer.unit` or `reader.unit` matches the assigned unit; none are recorded.
- `project-structure.md` and `tech-stack.md`
  - Who reads: all implementation and planning workers.
  - Use for: package/layer structure and the existing build/runtime stack.
- No `migration_boundary.yaml` or `seams.yaml` is included: this analysis was requested for the existing project, not a migration or partial cutover.

### Unit: application-startup

- External trigger: launching the Java application (`cli`).
- Must read:
  - `units/application-startup/behavior.yaml` — startup-time dataset loading, classifier behavior, and failure paths.
  - `units/application-startup/bindings.yaml` — Spring Boot startup and component wiring.
  - `units/application-startup/unit_decomposition.yaml` — advisory split candidates only; `commit: false`.
- Relevant global rows:
  - `wire_contracts.yaml`: contracts where `unit` is `application-startup` or `global`.
  - `shared_modules.yaml`: rows where `used_by_units` includes `application-startup`.
  - `cross_unit_state.yaml`: flows touching `application-startup`.
- Before DONE report: artifact paths read, preserved behavior, unresolved contracts, and test/build/runtime evidence.

