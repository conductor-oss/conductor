/*
 * Copyright 2025 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.ai.agentspan.runtime.controller;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.model.StartRequest;
import org.conductoross.conductor.ai.agentspan.runtime.model.StartResponse;
import org.conductoross.conductor.ai.agentspan.runtime.model.skill.SkillDeployRequest;
import org.conductoross.conductor.ai.agentspan.runtime.model.skill.SkillDetail;
import org.conductoross.conductor.ai.agentspan.runtime.model.skill.SkillFileContent;
import org.conductoross.conductor.ai.agentspan.runtime.model.skill.SkillSummary;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.ai.agentspan.runtime.service.SkillRegistryService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Component
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillRegistryService skillRegistryService;
    private final AgentService agentService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SkillDetail registerSkill(
            @RequestPart("manifest") String manifest,
            @RequestPart("package") MultipartFile packageFile) {
        return skillRegistryService.register(manifest, packageFile);
    }

    @GetMapping
    public List<SkillSummary> listSkills(
            @RequestParam(name = "allVersions", defaultValue = "false") boolean allVersions) {
        return skillRegistryService.list(allVersions);
    }

    @GetMapping("/{name}")
    public SkillDetail getLatestSkill(@PathVariable("name") String name) {
        return skillRegistryService.get(name, null);
    }

    @GetMapping("/{name}/versions/{version}")
    public SkillDetail getSkill(
            @PathVariable("name") String name, @PathVariable("version") String version) {
        return skillRegistryService.get(name, version);
    }

    @GetMapping("/{name}/versions/{version}/files")
    public SkillFileContent readSkillFile(
            @PathVariable("name") String name,
            @PathVariable("version") String version,
            @RequestParam("path") String path) {
        return skillRegistryService.readFile(name, version, path);
    }

    @GetMapping("/{name}/versions/{version}/package")
    public ResponseEntity<ByteArrayResource> downloadSkillPackage(
            @PathVariable("name") String name, @PathVariable("version") String version) {
        byte[] bytes = skillRegistryService.packageBytes(name, version);
        String filename = name + "-" + version + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(new ByteArrayResource(bytes));
    }

    @PostMapping("/{name}/versions/{version}/deploy")
    public StartResponse deploySkill(
            @PathVariable("name") String name,
            @PathVariable("version") String version,
            @RequestBody(required = false) SkillDeployRequest request) {
        Map<String, Object> rawConfig =
                skillRegistryService.rawConfigForDeploy(
                        name,
                        version,
                        request != null ? request.getModel() : null,
                        request != null ? request.getAgentModels() : null);
        return agentService.deploy(
                StartRequest.builder()
                        .framework("skill")
                        .rawConfig(rawConfig)
                        .skillRef(
                                Map.of(
                                        "name",
                                        name,
                                        "version",
                                        skillRegistryService.get(name, version).getVersion()))
                        .build());
    }

    @DeleteMapping("/{name}/versions/{version}")
    public ResponseEntity<Void> deleteSkill(
            @PathVariable("name") String name, @PathVariable("version") String version) {
        skillRegistryService.delete(name, version);
        return ResponseEntity.noContent().build();
    }
}
