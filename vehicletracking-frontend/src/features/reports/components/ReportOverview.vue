<script setup lang="ts">
import { Route, CircleCheck, Clock, MapPin, TriangleAlert, ArrowRight } from '@lucide/vue';
import type { OperationalReportDetail } from '@/features/reports/types/reports';
import type { ReportSection } from '@/features/reports/types/reportWorkspace';

defineProps<{ report: OperationalReportDetail }>();
const emit = defineEmits<{ select: [section: ReportSection] }>();
const number = (value: number) => value.toLocaleString('vi-VN');
</script>

<template>
  <section class="reports-summary-strip" aria-label="Tổng quan báo cáo">
    <article class="report-stat is-blue">
      <div class="report-stat-label"><Route :size="17" aria-hidden="true" /><span>Chuyến đã chạy</span></div>
      <strong>{{ number(report.summary.tripCount) }}</strong>
      <small>Mỗi chuyến tính một lần</small>
    </article>
    <article class="report-stat is-green">
      <div class="report-stat-label"><CircleCheck :size="17" aria-hidden="true" /><span>Chuyến hoàn thành</span></div>
      <strong>{{ number(report.summary.completedTripCount) }}</strong>
      <small>Đã kết thúc hành trình</small>
    </article>
    <article class="report-stat is-amber">
      <div class="report-stat-label"><Clock :size="17" aria-hidden="true" /><span>Chuyến về muộn</span></div>
      <strong>{{ number(report.summary.lateTripCount) }}</strong>
      <small>So với giờ kết thúc theo lịch</small>
    </article>
    <article class="report-stat is-rose">
      <div class="report-stat-label"><MapPin :size="17" aria-hidden="true" /><span>Lần đến trạm muộn</span></div>
      <strong>{{ number(report.lateStops.length) }}</strong>
      <button type="button" @click="emit('select', 'late-stops')">Xem trễ trạm <ArrowRight :size="14" aria-hidden="true" /></button>
    </article>
    <article class="report-stat is-orange">
      <div class="report-stat-label"><TriangleAlert :size="17" aria-hidden="true" /><span>Sự cố / cảnh báo an toàn</span></div>
      <strong>{{ number(report.incidents.reduce((sum, item) => sum + item.count, 0)) }}</strong>
      <button type="button" @click="emit('select', 'incidents')">Xem sự cố <ArrowRight :size="14" aria-hidden="true" /></button>
    </article>
  </section>
</template>
