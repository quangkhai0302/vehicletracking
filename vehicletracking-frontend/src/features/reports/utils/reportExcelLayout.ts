import type { CellObject, Feature, Row, Sheet } from 'write-excel-file/browser';
import {
  appendMarkupInsideElement, findElement, getCellAddress, getOrderOfSiblings, getSelfClosingTagMarkup,
  insertElementMarkupAccordingToOrderOfSiblings, replaceElement, sanitizeTextContent,
} from 'write-excel-file/utility';

export type ReportSheet = Sheet<File | Blob | ArrayBuffer>;
export type ExcelValue = string | number | Date | null;
type ColumnKind = 'text' | 'integer' | 'decimal' | 'percent' | 'date' | 'datetime' | 'status';
export interface ReportColumn { title: string; width: number; kind: ColumnKind }
export interface ReportSheetLayout {
  headerRow: number;
  lastDataRow: number;
  columnCount: number;
  bars?: { column: number; color: string; max?: number }[];
}
export const excelColors = {
  ink: '#18334B', navy: '#17364D', blue: '#EAF3F9', muted: '#5B7083',
  border: '#DCE6EF', stripe: '#F5F8FC', teal: '#007C91',
};

export function mergedRow(value: string, count: number, style: Partial<CellObject> = {}): Row {
  return [{ value, type: String, format: '@', columnSpan: count, fontSize: 11,
    textColor: excelColors.ink, alignVertical: 'center', wrap: true, height: 28, ...style },
  ...Array.from({ length: count - 1 }, () => null)];
}

export function reportCell(value: ExcelValue, column: ReportColumn, style: Partial<CellObject> = {}): CellObject {
  const base: Partial<CellObject> = {
    align: column.kind === 'text' ? 'left' : 'center', alignVertical: 'center', wrap: true,
    textColor: excelColors.ink, borderStyle: 'thin', borderColor: excelColors.border,
    ...style,
  };
  if (value == null || typeof value === 'string') {
    return { ...base, value: value ?? 'Chưa xác nhận', type: String, format: '@' };
  }
  if (value instanceof Date) {
    return { ...base, value, type: Date, format: column.kind === 'date' ? 'dd/mm/yyyy' : 'dd/mm/yyyy hh:mm' };
  }
  return { ...base, value: column.kind === 'percent' ? value / 100 : value, type: Number,
    format: column.kind === 'percent' ? '0.0%' : column.kind === 'decimal' ? '#,##0.00' : '#,##0' };
}

export function reportHeaders(columns: ReportColumn[]): Row {
  return columns.map((column) => reportCell(column.title, column, {
    fontWeight: 'bold', backgroundColor: excelColors.blue, height: 48,
  }));
}

/** XLSX dates have no time zone: store Vietnam's wall-clock fields in the serial value. */
export function excelDate(value: string | null): Date | null {
  if (!value) return null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return new Date(`${value}T00:00:00Z`);
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(value));
  const part = (name: string) => Number(parts.find((item) => item.type === name)!.value);
  return new Date(Date.UTC(part('year'), part('month') - 1, part('day'), part('hour'), part('minute'), part('second')));
}

/** Supported writer extension: filters exclude totals/notes; bars keep numeric cell values. */
export function reportExcelFeatures(layouts: (ReportSheetLayout | null)[]): Feature<File | Blob | ArrayBuffer> {
  return { files: { transform: {
    'xl/workbook.xml': {
      transform(xml, sheets) {
        const titles = layouts.map((layout, index) => {
          if (!layout) return '';
          const name = sheets[index]!.sheet!.replace(/'/g, "''");
          return `<definedName name="_xlnm.Print_Titles" localSheetId="${index}">${sanitizeTextContent(`'${name}'!$1:$${layout.headerRow}`)}</definedName>`;
        }).join('');
        return appendMarkupInsideElement(xml, findElement(xml, 'definedNames')!, titles);
      },
    },
    'xl/worksheets/sheet{id}.xml': {
      transform(xml, _options, { sheetIndex }) {
        const order = getOrderOfSiblings('xl/worksheets/sheet{id}.xml', 'worksheet')!;
        const put = (name: string, markup: string) => {
          const existing = findElement(xml, name);
          xml = existing ? replaceElement(xml, existing, markup)
            : insertElementMarkupAccordingToOrderOfSiblings(xml, markup, order, 'worksheet');
        };
        put('sheetPr', '<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>');
        const layout = layouts[sheetIndex];
        const setup = findElement(xml, 'pageSetup');
        put('pageSetup', getSelfClosingTagMarkup('pageSetup', {
          ...setup?.openingTagAttributes, orientation: 'landscape', paperSize: 9, fitToWidth: 1, fitToHeight: layout ? 0 : 1,
        }));
        if (!layout || layout.lastDataRow <= layout.headerRow) return xml;
        const range = `${getCellAddress(layout.headerRow - 1, 0)}:${getCellAddress(layout.lastDataRow - 1, layout.columnCount - 1)}`;
        put('autoFilter', getSelfClosingTagMarkup('autoFilter', { ref: range }));
        for (const [index, bar] of (layout.bars ?? []).entries()) {
          const cells = `${getCellAddress(layout.headerRow, bar.column)}:${getCellAddress(layout.lastDataRow - 1, bar.column)}`;
          const upper = bar.max == null ? '<cfvo type="max"/>' : `<cfvo type="num" val="${bar.max}"/>`;
          const markup = `<conditionalFormatting sqref="${cells}"><cfRule type="dataBar" priority="${index + 1}"><dataBar showValue="1" minLength="0" maxLength="100"><cfvo type="num" val="0"/>${upper}<color rgb="FF${bar.color}"/></dataBar></cfRule></conditionalFormatting>`;
          xml = insertElementMarkupAccordingToOrderOfSiblings(xml, markup, order, 'worksheet');
        }
        return xml;
      },
    },
  } } };
}
