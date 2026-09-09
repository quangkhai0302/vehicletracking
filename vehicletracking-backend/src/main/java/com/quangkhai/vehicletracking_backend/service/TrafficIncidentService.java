package com.quangkhai.vehiceltracking_backend.service;

import com.quangkhai.vehiceltracking_backend.dto.AlertMessageDto;
import com.quangkhai.vehiceltracking_backend.dto.TrafficIncidentDto;
import com.quangkhai.vehiceltracking_backend.entity.TrafficIncident;
import com.quangkhai.vehiceltracking_backend.enums.IncidentType;
import com.quangkhai.vehiceltracking_backend.repository.TrafficIncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrafficIncidentService {

    private final TrafficIncidentRepository incidentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<TrafficIncidentDto> getAllIncidents() {
        return incidentRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<TrafficIncidentDto> getActiveIncidents() {
        return incidentRepository.findByActiveTrue().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TrafficIncidentDto createIncident(TrafficIncidentDto dto) {
        TrafficIncident incident = TrafficIncident.builder()
                .title(dto.getTitle().trim())
                .type(dto.getType() != null ? dto.getType() : IncidentType.CONGESTION)
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .radiusMeters(dto.getRadiusMeters() != null ? dto.getRadiusMeters() : 250.0)
                .speedReductionPercent(dto.getSpeedReductionPercent() != null ? dto.getSpeedReductionPercent() : 60.0)
                .description(dto.getDescription())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        TrafficIncident saved = incidentRepository.save(incident);

        // Bắn thông báo sự cố mới qua WebSocket
        AlertMessageDto alert = AlertMessageDto.builder()
                .id(UUID.randomUUID().toString())
                .level("WARNING")
                .title("Cảnh báo sự cố mới: " + saved.getTitle())
                .message("Phát hiện điểm " + saved.getType().name() + " tại tọa độ [" + saved.getLatitude() + ", " + saved.getLongitude() + "]. Bán kính ảnh hưởng: " + saved.getRadiusMeters() + "m.")
                .incidentId(saved.getId())
                .timestamp(LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/topic/alerts", alert);

        return toDto(saved);
    }

    @Transactional
    public TrafficIncidentDto toggleIncident(Long id) {
        TrafficIncident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sự cố với ID: " + id));

        incident.setActive(!Boolean.TRUE.equals(incident.getActive()));
        TrafficIncident saved = incidentRepository.save(incident);

        AlertMessageDto alert = AlertMessageDto.builder()
                .id(UUID.randomUUID().toString())
                .level("INFO")
                .title("Cập nhật sự cố: " + saved.getTitle())
                .message("Trạng thái sự cố đã đổi thành: " + (Boolean.TRUE.equals(saved.getActive()) ? "ĐANG CÒN HIỆU LỰC" : "ĐÃ GIẢI TỎA"))
                .incidentId(saved.getId())
                .timestamp(LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/topic/alerts", alert);

        return toDto(saved);
    }

    @Transactional
    public void deleteIncident(Long id) {
        if (!incidentRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy sự cố với ID: " + id);
        }
        incidentRepository.deleteById(id);
    }

    public TrafficIncidentDto toDto(TrafficIncident i) {
        return TrafficIncidentDto.builder()
                .id(i.getId())
                .title(i.getTitle())
                .type(i.getType())
                .latitude(i.getLatitude())
                .longitude(i.getLongitude())
                .radiusMeters(i.getRadiusMeters())
                .speedReductionPercent(i.getSpeedReductionPercent())
                .description(i.getDescription())
                .active(i.getActive())
                .createdAt(i.getCreatedAt())
                .build();
    }
}
