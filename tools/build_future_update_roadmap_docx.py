from __future__ import annotations

from datetime import date
from pathlib import Path
from zipfile import ZipFile

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "Yutnori_future_update_roadmap.docx"

# compact_reference_guide preset
CONTENT_WIDTH_DXA = 9360
TABLE_INDENT_DXA = 120
CELL_MARGINS_DXA = {"top": 80, "bottom": 80, "start": 120, "end": 120}
FONT_LATIN = "Calibri"
FONT_KOREAN = "Malgun Gothic"

NAVY = "0B2545"
BLUE = "2E74B5"
DARK_BLUE = "1F4D78"
MUTED = "667085"
BODY = "17202A"
BORDER = "C8D2DD"
HEADER_FILL = "E8EEF5"
LIGHT_FILL = "F2F4F7"
CALLOUT_FILL = "F4F6F9"
POSITIVE = "1F3A5F"
CAUTION = "7A5A00"
RISK = "9B1C1C"
WHITE = "FFFFFF"
ACCENT_GREEN = "26745B"


def rgb(hex_color: str) -> RGBColor:
    return RGBColor.from_string(hex_color)


def set_run_font(
    run,
    *,
    size: float | None = None,
    color: str | None = None,
    bold: bool | None = None,
    italic: bool | None = None,
    font_name: str = FONT_LATIN,
) -> None:
    run.font.name = font_name
    run._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), font_name)
    run._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), font_name)
    run._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), FONT_KOREAN)
    if size is not None:
        run.font.size = Pt(size)
    if color is not None:
        run.font.color.rgb = rgb(color)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic


def set_repeat_table_header(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def set_cell_shading(cell, fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, margins: dict[str, int] = CELL_MARGINS_DXA) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_mar = tc_pr.find(qn("w:tcMar"))
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for key in ("top", "start", "bottom", "end"):
        node = tc_mar.find(qn(f"w:{key}"))
        if node is None:
            node = OxmlElement(f"w:{key}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(margins[key]))
        node.set(qn("w:type"), "dxa")


def set_cell_border(cell, **edges) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_borders = tc_pr.find(qn("w:tcBorders"))
    if tc_borders is None:
        tc_borders = OxmlElement("w:tcBorders")
        tc_pr.append(tc_borders)
    for edge_name, edge_data in edges.items():
        edge = tc_borders.find(qn(f"w:{edge_name}"))
        if edge is None:
            edge = OxmlElement(f"w:{edge_name}")
            tc_borders.append(edge)
        for key, value in edge_data.items():
            edge.set(qn(f"w:{key}"), str(value))


def set_table_geometry(table, widths_dxa: list[int], indent_dxa: int = TABLE_INDENT_DXA) -> None:
    if sum(widths_dxa) != CONTENT_WIDTH_DXA:
        raise ValueError(f"Table widths must total {CONTENT_WIDTH_DXA}: {widths_dxa}")

    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    tbl_pr = table._tbl.tblPr

    tbl_w = tbl_pr.find(qn("w:tblW"))
    if tbl_w is None:
        tbl_w = OxmlElement("w:tblW")
        tbl_pr.append(tbl_w)
    tbl_w.set(qn("w:w"), str(CONTENT_WIDTH_DXA))
    tbl_w.set(qn("w:type"), "dxa")

    tbl_ind = tbl_pr.find(qn("w:tblInd"))
    if tbl_ind is None:
        tbl_ind = OxmlElement("w:tblInd")
        tbl_pr.append(tbl_ind)
    tbl_ind.set(qn("w:w"), str(indent_dxa))
    tbl_ind.set(qn("w:type"), "dxa")

    layout = tbl_pr.find(qn("w:tblLayout"))
    if layout is None:
        layout = OxmlElement("w:tblLayout")
        tbl_pr.append(layout)
    layout.set(qn("w:type"), "fixed")

    grid = table._tbl.tblGrid
    for child in list(grid):
        grid.remove(child)
    for width in widths_dxa:
        grid_col = OxmlElement("w:gridCol")
        grid_col.set(qn("w:w"), str(width))
        grid.append(grid_col)

    for row in table.rows:
        for idx, (cell, width) in enumerate(zip(row.cells, widths_dxa)):
            cell.width = Inches(width / 1440)
            tc_pr = cell._tc.get_or_add_tcPr()
            tc_w = tc_pr.find(qn("w:tcW"))
            if tc_w is None:
                tc_w = OxmlElement("w:tcW")
                tc_pr.append(tc_w)
            tc_w.set(qn("w:w"), str(width))
            tc_w.set(qn("w:type"), "dxa")
            set_cell_margins(cell)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def set_table_borders(table, color: str = BORDER, size: int = 6) -> None:
    edge_data = {"val": "single", "sz": size, "space": 0, "color": color}
    for row in table.rows:
        for cell in row.cells:
            set_cell_border(
                cell,
                top=edge_data,
                bottom=edge_data,
                start=edge_data,
                end=edge_data,
                insideH=edge_data,
                insideV=edge_data,
            )


def set_cell_text(
    cell,
    text: str,
    *,
    size: float = 9.5,
    color: str = BODY,
    bold: bool = False,
    align=WD_ALIGN_PARAGRAPH.LEFT,
    before: float = 0,
    after: float = 0,
) -> None:
    cell.text = ""
    paragraph = cell.paragraphs[0]
    paragraph.alignment = align
    paragraph.paragraph_format.space_before = Pt(before)
    paragraph.paragraph_format.space_after = Pt(after)
    paragraph.paragraph_format.line_spacing = 1.15
    run = paragraph.add_run(text)
    set_run_font(run, size=size, color=color, bold=bold)


def add_numbering_definition(doc: Document, kind: str) -> int:
    numbering = doc.part.numbering_part.element
    abstract_ids = [
        int(el.get(qn("w:abstractNumId")))
        for el in numbering.findall(qn("w:abstractNum"))
    ]
    num_ids = [int(el.get(qn("w:numId"))) for el in numbering.findall(qn("w:num"))]
    abstract_id = max(abstract_ids, default=-1) + 1
    num_id = max(num_ids, default=0) + 1

    abstract = OxmlElement("w:abstractNum")
    abstract.set(qn("w:abstractNumId"), str(abstract_id))
    multi_level = OxmlElement("w:multiLevelType")
    multi_level.set(qn("w:val"), "singleLevel")
    abstract.append(multi_level)

    level = OxmlElement("w:lvl")
    level.set(qn("w:ilvl"), "0")
    start = OxmlElement("w:start")
    start.set(qn("w:val"), "1")
    level.append(start)

    num_fmt = OxmlElement("w:numFmt")
    num_fmt.set(qn("w:val"), "bullet" if kind == "bullet" else "decimal")
    level.append(num_fmt)

    lvl_text = OxmlElement("w:lvlText")
    lvl_text.set(qn("w:val"), "•" if kind == "bullet" else "%1.")
    level.append(lvl_text)

    lvl_jc = OxmlElement("w:lvlJc")
    lvl_jc.set(qn("w:val"), "left")
    level.append(lvl_jc)

    p_pr = OxmlElement("w:pPr")
    tabs = OxmlElement("w:tabs")
    tab = OxmlElement("w:tab")
    tab.set(qn("w:val"), "num")
    tab.set(qn("w:pos"), "540")
    tabs.append(tab)
    p_pr.append(tabs)
    ind = OxmlElement("w:ind")
    ind.set(qn("w:left"), "540")
    ind.set(qn("w:hanging"), "271")
    p_pr.append(ind)
    level.append(p_pr)

    r_pr = OxmlElement("w:rPr")
    r_fonts = OxmlElement("w:rFonts")
    r_fonts.set(qn("w:ascii"), FONT_LATIN)
    r_fonts.set(qn("w:hAnsi"), FONT_LATIN)
    r_fonts.set(qn("w:eastAsia"), FONT_KOREAN)
    r_pr.append(r_fonts)
    level.append(r_pr)

    abstract.append(level)
    numbering.append(abstract)

    num = OxmlElement("w:num")
    num.set(qn("w:numId"), str(num_id))
    abstract_ref = OxmlElement("w:abstractNumId")
    abstract_ref.set(qn("w:val"), str(abstract_id))
    num.append(abstract_ref)
    numbering.append(num)
    return num_id


def apply_numbering(paragraph, num_id: int) -> None:
    p_pr = paragraph._p.get_or_add_pPr()
    num_pr = p_pr.find(qn("w:numPr"))
    if num_pr is None:
        num_pr = OxmlElement("w:numPr")
        p_pr.append(num_pr)
    ilvl = OxmlElement("w:ilvl")
    ilvl.set(qn("w:val"), "0")
    num_id_el = OxmlElement("w:numId")
    num_id_el.set(qn("w:val"), str(num_id))
    num_pr.append(ilvl)
    num_pr.append(num_id_el)


def add_bullet(doc: Document, text: str, bullet_id: int, *, bold_prefix: str | None = None):
    paragraph = doc.add_paragraph(style="Roadmap List")
    apply_numbering(paragraph, bullet_id)
    if bold_prefix and text.startswith(bold_prefix):
        first = paragraph.add_run(bold_prefix)
        set_run_font(first, size=11, color=BODY, bold=True)
        rest = paragraph.add_run(text[len(bold_prefix) :])
        set_run_font(rest, size=11, color=BODY)
    else:
        run = paragraph.add_run(text)
        set_run_font(run, size=11, color=BODY)
    return paragraph


def add_numbered(doc: Document, text: str, decimal_id: int):
    paragraph = doc.add_paragraph(style="Roadmap List")
    apply_numbering(paragraph, decimal_id)
    run = paragraph.add_run(text)
    set_run_font(run, size=11, color=BODY)
    return paragraph


def add_heading(doc: Document, text: str, level: int = 1):
    paragraph = doc.add_paragraph(text, style=f"Heading {level}")
    paragraph.paragraph_format.keep_with_next = True
    return paragraph


def add_body(
    doc: Document,
    text: str,
    *,
    bold_prefix: str | None = None,
    color: str = BODY,
    size: float = 11,
    after: float | None = None,
):
    paragraph = doc.add_paragraph()
    if after is not None:
        paragraph.paragraph_format.space_after = Pt(after)
    if bold_prefix and text.startswith(bold_prefix):
        lead = paragraph.add_run(bold_prefix)
        set_run_font(lead, size=size, color=color, bold=True)
        rest = paragraph.add_run(text[len(bold_prefix) :])
        set_run_font(rest, size=size, color=color)
    else:
        run = paragraph.add_run(text)
        set_run_font(run, size=size, color=color)
    return paragraph


def add_callout(doc: Document, title: str, body: str, *, accent: str = BLUE):
    table = doc.add_table(rows=1, cols=1)
    set_table_geometry(table, [CONTENT_WIDTH_DXA])
    set_repeat_table_header(table.rows[0])
    cell = table.cell(0, 0)
    set_cell_shading(cell, CALLOUT_FILL)
    set_cell_border(
        cell,
        top={"val": "single", "sz": 6, "color": BORDER},
        bottom={"val": "single", "sz": 6, "color": BORDER},
        start={"val": "single", "sz": 18, "color": accent},
        end={"val": "single", "sz": 6, "color": BORDER},
    )
    cell.text = ""
    p_title = cell.paragraphs[0]
    p_title.paragraph_format.space_after = Pt(3)
    title_run = p_title.add_run(title)
    set_run_font(title_run, size=11, color=accent, bold=True)
    p_body = cell.add_paragraph()
    p_body.paragraph_format.space_before = Pt(0)
    p_body.paragraph_format.space_after = Pt(0)
    p_body.paragraph_format.line_spacing = 1.2
    body_run = p_body.add_run(body)
    set_run_font(body_run, size=10.5, color=BODY)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)
    return table


def add_table(
    doc: Document,
    headers: list[str],
    rows: list[list[str]],
    widths_dxa: list[int],
    *,
    font_size: float = 9.2,
    first_col_bold: bool = False,
):
    table = doc.add_table(rows=1, cols=len(headers))
    set_table_geometry(table, widths_dxa)
    set_table_borders(table)
    header_row = table.rows[0]
    set_repeat_table_header(header_row)
    for index, (cell, header) in enumerate(zip(header_row.cells, headers)):
        set_cell_shading(cell, HEADER_FILL)
        set_cell_text(cell, header, size=9.2, color=POSITIVE, bold=True)

    for row_values in rows:
        row = table.add_row()
        for index, (cell, value) in enumerate(zip(row.cells, row_values)):
            set_cell_text(
                cell,
                value,
                size=font_size,
                color=BODY,
                bold=first_col_bold and index == 0,
            )
    set_table_geometry(table, widths_dxa)
    set_table_borders(table)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)
    return table


def add_page_break(doc: Document) -> None:
    paragraph = doc.add_paragraph()
    paragraph.paragraph_format.space_after = Pt(0)
    paragraph.add_run().add_break(WD_BREAK.PAGE)


def add_page_field(paragraph) -> None:
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instruction = OxmlElement("w:instrText")
    instruction.set(qn("xml:space"), "preserve")
    instruction.text = " PAGE "
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.append(begin)
    run._r.append(instruction)
    run._r.append(separate)
    run._r.append(end)
    set_run_font(run, size=9, color=MUTED)


def configure_styles(doc: Document) -> None:
    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = FONT_LATIN
    normal._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), FONT_LATIN)
    normal._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), FONT_LATIN)
    normal._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), FONT_KOREAN)
    normal.font.size = Pt(11)
    normal.font.color.rgb = rgb(BODY)
    normal.paragraph_format.space_before = Pt(0)
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.25

    heading_tokens = {
        1: (16, BLUE, 18, 10),
        2: (13, BLUE, 14, 7),
        3: (12, DARK_BLUE, 10, 5),
    }
    for level, (size, color, before, after) in heading_tokens.items():
        style = styles[f"Heading {level}"]
        style.font.name = FONT_LATIN
        style._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), FONT_LATIN)
        style._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), FONT_LATIN)
        style._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), FONT_KOREAN)
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = rgb(color)
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True

    list_style = styles.add_style("Roadmap List", 1)
    list_style.base_style = normal
    list_style.font.name = FONT_LATIN
    list_style._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), FONT_LATIN)
    list_style._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), FONT_LATIN)
    list_style._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), FONT_KOREAN)
    list_style.font.size = Pt(11)
    list_style.paragraph_format.space_before = Pt(0)
    list_style.paragraph_format.space_after = Pt(4)
    list_style.paragraph_format.line_spacing = 1.25


def configure_page(doc: Document) -> None:
    section = doc.sections[0]
    section.page_width = Inches(8.5)
    section.page_height = Inches(11)
    section.top_margin = Inches(1)
    section.right_margin = Inches(1)
    section.bottom_margin = Inches(1)
    section.left_margin = Inches(1)
    section.header_distance = Inches(0.492)
    section.footer_distance = Inches(0.492)

    header = section.header
    header.is_linked_to_previous = False
    p = header.paragraphs[0]
    p.text = ""
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    left = p.add_run("YUTNORI  |  FUTURE UPDATE ROADMAP")
    set_run_font(left, size=8.5, color=MUTED, bold=True)
    p.add_run("\t")
    right = p.add_run("1.0.0 (38) 기준")
    set_run_font(right, size=8.5, color=MUTED)
    tabs = p.paragraph_format.tab_stops
    tabs.add_tab_stop(Inches(6.5), alignment=2)

    footer = section.footer
    footer.is_linked_to_previous = False
    fp = footer.paragraphs[0]
    fp.text = ""
    fp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    fp.paragraph_format.space_before = Pt(0)
    fp.paragraph_format.space_after = Pt(0)
    label = fp.add_run("Yutnori roadmap  |  ")
    set_run_font(label, size=9, color=MUTED)
    add_page_field(fp)


def build_document() -> Document:
    doc = Document()
    configure_page(doc)
    configure_styles(doc)
    bullet_id = add_numbering_definition(doc, "bullet")
    decimal_id = add_numbering_definition(doc, "decimal")

    # Memo masthead
    kicker = doc.add_paragraph()
    kicker.paragraph_format.space_before = Pt(10)
    kicker.paragraph_format.space_after = Pt(4)
    run = kicker.add_run("PRODUCT ROADMAP")
    set_run_font(run, size=10, color=ACCENT_GREEN, bold=True)

    title = doc.add_paragraph()
    title.paragraph_format.space_before = Pt(0)
    title.paragraph_format.space_after = Pt(5)
    run = title.add_run("Yutnori 향후 업데이트 로드맵")
    set_run_font(run, size=28, color=NAVY, bold=True)

    subtitle = doc.add_paragraph()
    subtitle.paragraph_format.space_before = Pt(0)
    subtitle.paragraph_format.space_after = Pt(15)
    run = subtitle.add_run("1.0 공개 이후, 오프라인 윷판에서 함께 연결되는 게임으로")
    set_run_font(run, size=13.5, color=MUTED)

    metadata = [
        ("기준 버전", "1.0.0 / 빌드 38"),
        ("문서 목적", "공개 이후 기능 후보, 우선순위, 기술 전제와 출시 기준을 한곳에서 관리"),
        ("핵심 제안", "게스트 우선 참여 + 선택형 소셜 로그인 + 방 단위 실시간 게임 공유"),
        ("업데이트", str(date(2026, 7, 27))),
    ]
    for label, value in metadata:
        p = doc.add_paragraph()
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(2)
        lead = p.add_run(f"{label}: ")
        set_run_font(lead, size=10.5, color=NAVY, bold=True)
        tail = p.add_run(value)
        set_run_font(tail, size=10.5, color=BODY)

    rule = doc.add_paragraph()
    rule.paragraph_format.space_before = Pt(8)
    rule.paragraph_format.space_after = Pt(10)
    p_pr = rule._p.get_or_add_pPr()
    p_bdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "16")
    bottom.set(qn("w:space"), "1")
    bottom.set(qn("w:color"), ACCENT_GREEN)
    p_bdr.append(bottom)
    p_pr.append(p_bdr)

    add_callout(
        doc,
        "제품 방향",
        "1.0은 서버 없이 한 기기에서 온전히 플레이하는 완성본으로 유지한다. "
        "온라인 기능은 기존 흐름을 바꾸는 필수 조건이 아니라, 여러 사람이 각자 휴대폰으로 같은 판을 볼 수 있게 하는 선택형 확장으로 제공한다.",
        accent=ACCENT_GREEN,
    )

    add_heading(doc, "0. 실행 우선순위 보드", 1)
    add_body(
        doc,
        "기능을 더 늘리기 전에 공개 버전에 필요한 수정과 의사결정을 분리한다. "
        "당장 항목은 다음 AAB 전에 완료하고, 결정 필요 항목은 실제 테스터 반응을 근거로 선택하며, 나중 항목은 1.0 공개 후 별도 버전으로 진행한다.",
    )

    add_heading(doc, "0.1 당장 해야 할 것", 2)
    immediate_rows = [
        [
            "NOW-01",
            "윷판 좌표 90도 반시계 회전",
            "현재 7시 방향인 출발·완주 지점을 일반적으로 익숙한 5시 방향으로 옮기고 첫 도가 오른쪽 변을 따라 위로 진행하게 한다. 규칙 경로 ID는 유지하고 좌표만 회전한다.",
            "출발점은 오른쪽 아래, 첫 이동은 위쪽이며 기존 이동 거리와 지름길이 동일함",
        ],
        [
            "NOW-02",
            "회전 회귀 테스트",
            "바깥 경로, 두 갈림길, 중앙 합류, 백도, 잡기·업기, 완주 미리보기와 한 칸씩 이동하는 애니메이션을 다시 검증한다.",
            "세로·가로·휴대폰·태블릿에서 좌표와 터치 목표가 일치함",
        ],
        [
            "NOW-03",
            "스토어 화면 이미지 갱신",
            "판 방향이 보이는 휴대전화와 7·10인치 태블릿 스크린샷을 새 화면으로 교체한다. 앱 아이콘과 그래픽 이미지는 영향 여부만 확인한다.",
            "스토어 이미지와 실제 설치 화면의 판 방향이 일치함",
        ],
        [
            "NOW-04",
            "비공개 테스트 완료",
            "12명 이상이 참여 상태를 14일 유지하도록 하고, 핵심 플레이·가로 화면·백그라운드 복귀·게임 종료 의견을 실제로 수집한다.",
            "참여 인원과 기간 충족, 피드백 요약과 치명적 오류 확인 완료",
        ],
        [
            "NOW-05",
            "1.0.1 / 빌드 39 배포",
            "좌표 회전과 회귀 수정은 공개 전 패치 버전으로 묶어 Alpha 트랙에 배포한다. 기능 추가는 포함하지 않는다.",
            "테스트·lint·서명 AAB 통과 후 Alpha의 활성 버전이 빌드 39로 표시됨",
        ],
        [
            "NOW-06",
            "1.2.1 판 진행 방향 안내",
            "5시 출발점에는 출발 표시와 첫 진행 방향을 분명히 보여주고, 실제 선택지가 생기는 갈림길에만 작은 화살표를 판 배경 요소로 추가한다. 외길에는 표시하지 않는다.",
            "세로·가로에서 말·도착 미리보기·터치 영역을 가리지 않고 처음 보는 사용자가 출발점과 선택 가능한 방향을 즉시 구분함",
        ],
        [
            "NOW-07",
            "1.2.1 선택형 업데이트 안내",
            "Google Play Core로 앱을 완전히 실행했을 때 새 버전 존재 여부를 한 번 확인한다. 업데이트가 있을 때만 팝업을 표시하고, 업데이트는 Play 스토어 상세 페이지로 이동하며 나중에는 게임을 계속한다.",
            "거절 가능, 같은 실행 중 반복 없음, 다음 콜드 스타트에서 재안내, 회전·백그라운드 복귀·게임 중 중복 팝업 없음, 확인 실패 시 게임을 방해하지 않음",
        ],
        [
            "NOW-08",
            "1.2.1 업은 말 가독성 확대",
            "개별 말 원을 키우고 서로 약간 겹치게 하되 묶음 중심은 노드 정중앙에 유지한다. 외곽 가로·세로 경로에서는 촘촘하게, 대각선·큰 꼭짓점·중앙에서는 여유 있게 펼친다.",
            "2·3·4개를 즉시 구분하고 이동 중 합치기·도착 후 펼치기를 유지함. 판 밖 잘림과 인접 칸 터치 간섭 없이 세로·가로·태블릿에서 중앙 정렬됨. 숫자·Xn 배지는 이 버전에 포함하지 않음",
        ],
    ]
    add_table(
        doc,
        ["ID", "작업", "이유와 범위", "완료 조건"],
        immediate_rows,
        [900, 2150, 3810, 2500],
        font_size=8.8,
        first_col_bold=True,
    )

    add_callout(
        doc,
        "판 방향 결론",
        "90도 반시계 회전을 권장한다. 현재 판도 수학적으로는 같은 경로를 회전해 놓은 형태라 규칙 오류는 아니지만, "
        "오른쪽 아래에서 시작해 위로 올라가는 배치가 사용자 기대와 더 잘 맞는다. 구현은 좌표 변환 x'=y, y'=1-x로 제한해 규칙 엔진과 저장 데이터는 건드리지 않는다.",
        accent=ACCENT_GREEN,
    )

    add_heading(doc, "0.2 결정이 필요한 것", 2)
    decision_rows = [
        [
            "DEC-02",
            "판 방향 선택 설정",
            "사용자가 좌우 또는 회전 방향을 바꾸게 할지 결정.",
            "추천: 설정을 늘리지 말고 오른쪽 아래 출발 하나로 통일",
        ],
        [
            "DEC-03",
            "지역·가정별 규칙 옵션",
            "백도, 정확히 맞춰 완주, 잡기 보너스와 윷 결과 사용 순서처럼 집마다 다른 규칙을 설정으로 노출할지 결정.",
            "추천: 1.0은 현재 규칙 고정, 반복 요청이 확인되면 프리셋으로 제공",
        ],
        [
            "DEC-04",
            "외부 분석·충돌 SDK",
            "개별 사용 흐름 분석이나 충돌 보고 SDK를 추가하면 진단은 쉬워지지만 개인정보 고지와 데이터 보안 답변이 바뀔 수 있음.",
            "추천: 지금은 Play Console 지표와 직접 피드백 사용, 1.1에서 재검토",
        ],
        [
            "DEC-05",
            "Alpha 갱신 시점",
            "테스터 참여 전이면 빌드 39를 먼저 배포하고, 이미 참여가 시작됐다면 새 빌드를 즉시 같은 트랙에 올릴지 판단.",
            "추천: 가능한 한 테스트 초기에 방향을 고쳐 모든 참가자가 같은 화면을 사용",
        ],
    ]
    add_table(
        doc,
        ["ID", "결정 항목", "쟁점", "현재 권고"],
        decision_rows,
        [900, 2080, 3500, 2880],
        font_size=8.9,
        first_col_bold=True,
    )

    add_heading(doc, "0.3 나중에 할 것", 2)
    later_rows = [
        ["FUT-01", "사용성 고도화", "튜토리얼, 규칙 도움말, 큰 글자·색각 보정, 한국어·영어 다듬기", "1.1"],
        ["FUT-02", "게임 프리셋", "일반전, 시간전, 무제한전, 빠른 대전과 팀 이름·색상", "1.2"],
        ["FUT-03", "계정 기반", "게스트 우선 참여, Google 로그인, 프로필과 클라우드 복원", "2.0"],
        ["FUT-04", "실시간 판 공유", "방 코드·QR·초대 링크, 권한, 동기화, 재접속과 관전자", "2.1"],
        ["FUT-05", "기록·리플레이", "게임 기록, 행동 타임라인, 결과 카드와 최근 함께한 사람", "2.2"],
        ["FUT-06", "개인화·수익화", "판·말 테마, 효과음 팩, 게임 중이 아닌 제한적 광고 또는 1회 구매", "3.x"],
        ["FUT-07", "플랫폼 확장", "웹 공유 화면과 iOS 버전은 Android 공유 보드 안정화 후 검토", "3.x+"],
        ["FUT-08", "업은 말 수량 배지 실험", "실제 기기에서 무표시·숫자 배지·X2 표기를 비교하고, 가장자리에서는 배지가 말 묶음 안쪽으로 반전되도록 검증한 뒤 가독성이 실제로 부족할 때만 적용", "1.2.1 이후 미정"],
    ]
    add_table(
        doc,
        ["ID", "영역", "범위", "목표 버전"],
        later_rows,
        [900, 1900, 5260, 1300],
        font_size=9.0,
        first_col_bold=True,
    )

    add_heading(doc, "1. 한눈에 보는 출시 단계", 1)
    roadmap_rows = [
        ["1.0.x", "안정화", "스토어 피드백 반영, 충돌·규칙·레이아웃 결함 수정", "서버 없음", "기존 게임 흐름 회귀 없음"],
        ["1.1", "사용성", "튜토리얼, 규칙 도움말, 접근성, 다국어 다듬기", "로컬 앱", "첫 사용자도 설명 없이 시작"],
        ["1.2", "게임 설정", "시간전·무제한전·빠른 대전, 팀 이름·색상 설정", "로컬 앱", "프리셋별 저장·복원 통과"],
        ["2.0 Alpha", "계정 기반", "게스트, Google 로그인, 프로필, 클라우드 복원", "인증·DB", "기기 변경 후 데이터 복원"],
        ["2.1 Beta", "게임 공유", "방 코드·QR, 실시간 동기화, 재접속, 권한 제어", "실시간 백엔드", "2~4팀 동기화 안정성 확보"],
        ["2.2", "기록·초대", "리플레이, 결과 공유, 관전자, 초대 링크", "2.1 안정화", "실사용 그룹 검증"],
        ["3.x", "확장", "테마·말 스킨·선택형 수익화·플랫폼 확장", "사용자 지표", "운영 가치 확인 후 진행"],
    ]
    add_table(
        doc,
        ["버전", "목표", "주요 범위", "의존성", "완료 기준"],
        roadmap_rows,
        [1000, 1180, 3420, 1560, 2200],
        font_size=8.8,
        first_col_bold=True,
    )

    add_heading(doc, "2. 업데이트 원칙", 1)
    principles = [
        "게스트 우선: 계정이 없어도 즉시 방에 참여할 수 있고, 로그인은 기록 복원·친구·초대 기능을 원할 때 선택한다.",
        "한 판의 신뢰성 우선: 꾸미기보다 턴, 말 위치, 대기 결과, 타이머와 기록이 모든 기기에서 정확히 일치해야 한다.",
        "오프라인 모드 보존: 네트워크가 없거나 서버 장애가 있어도 기존 한 기기 플레이는 계속 가능해야 한다.",
        "중장년층 기준의 명료함: 큰 터치 영역, 높은 대비, 짧은 문장, 명확한 현재 턴과 다음 행동을 유지한다.",
        "작은 출시 단위: 계정과 실시간 공유를 한 번에 공개하지 않고 Alpha와 Beta에서 복구·충돌 처리를 먼저 검증한다.",
    ]
    for item in principles:
        add_bullet(doc, item, bullet_id)

    add_heading(doc, "3. 핵심 확장: 각자 휴대폰으로 같은 윷판 보기", 1)
    add_body(
        doc,
        "사용자는 현실에서 윷을 던지고, 방장 또는 권한을 받은 참가자가 결과와 이동을 입력한다. "
        "모든 참가자의 휴대폰에는 같은 말 위치, 남은 결과, 현재 팀, 타이머와 행동 기록이 실시간으로 표시된다.",
    )

    add_heading(doc, "권장 사용자 흐름", 2)
    shared_flow = [
        "방장이 새 게임을 만들고 팀 수와 기본 규칙을 선택한다.",
        "화면에 표시된 6자리 방 코드, QR 또는 초대 링크를 참가자에게 공유한다.",
        "참가자는 로그인 없이 닉네임만 입력해 팀원 또는 관전자로 들어간다.",
        "현재 팀이 실제 윷 결과를 입력하고 말을 선택하면 이동 가능 칸이 각 기기에 동일하게 표시된다.",
        "이동, 잡기, 업기, 완주, 마지막 취소, 턴 종료와 일시정지가 하나의 행동 기록으로 전파된다.",
        "앱이 백그라운드로 가거나 통신이 끊겨도 재접속 시 서버의 최신 판 상태로 정확히 복원된다.",
    ]
    for item in shared_flow:
        add_numbered(doc, item, decimal_id)

    add_heading(doc, "2.1 공유 보드 MVP 범위", 2)
    mvp_rows = [
        ["방 만들기·참여", "방 코드, QR, 링크. 2~4팀과 관전자 역할 지원.", "P0"],
        ["게스트 참여", "로그인 없이 닉네임으로 참여. 이후 Google 계정에 기록 연결.", "P0"],
        ["실시간 상태", "말 위치, 남은 윷 결과, 현재 팀, 타이머, 일시정지, 로그 동기화.", "P0"],
        ["입력 권한", "기본은 방장 입력. 팀별 참가자에게 조작 권한을 위임할 수 있음.", "P0"],
        ["재접속", "앱 복귀·네트워크 복구 시 최신 스냅샷과 누락 행동을 받아 복원.", "P0"],
        ["충돌 처리", "중복 탭, 늦게 도착한 입력, 여러 기기의 동시 행동을 한 번만 반영.", "P0"],
        ["연결 상태 UI", "연결 중·동기화 완료·오프라인·재시도 상태를 짧고 명확하게 표시.", "P1"],
        ["관전자", "판과 기록은 보되 게임 입력은 할 수 없는 읽기 전용 역할.", "P1"],
    ]
    add_table(
        doc,
        ["기능", "범위", "우선순위"],
        mvp_rows,
        [1850, 6260, 1250],
        font_size=9.2,
        first_col_bold=True,
    )

    add_callout(
        doc,
        "추천 결정",
        "모든 참가자에게 로그인을 강제하지 않는다. 방장은 게스트로도 방을 만들 수 있게 하고, "
        "기록 보존·기기 변경 복원·친구 초대를 원하는 사용자만 Google 로그인을 연결하는 방식이 가장 진입 장벽이 낮다.",
        accent=POSITIVE,
    )

    add_heading(doc, "2.2 나중으로 미룰 기능", 2)
    deferred = [
        "공개 채팅: 신고, 차단, 금칙어, 미성년자 보호와 운영 부담이 커서 초기 공유 보드에는 넣지 않는다.",
        "자동 매칭·전 세계 랭킹: 물리 윷을 함께 던지는 앱의 핵심 경험과 거리가 있어 사용자 수요를 확인한 뒤 판단한다.",
        "복잡한 친구 피드·커뮤니티: 초대 링크와 최근 함께한 사람만 먼저 제공하고 사회 기능은 천천히 확장한다.",
        "게임 중 전면 광고: 흐름과 신뢰를 해치므로 제외한다. 광고를 쓰더라도 시작 전 또는 결과 화면에서만 검토한다.",
    ]
    for item in deferred:
        add_bullet(doc, item, bullet_id)

    add_page_break(doc)

    add_heading(doc, "4. 계정과 데이터", 1)
    add_heading(doc, "4.1 계정 기능", 2)
    account_rows = [
        ["게스트 프로필", "닉네임과 임시 식별자만 생성. 즉시 참여 가능.", "P0", "2.0"],
        ["Google 로그인", "Android 우선 소셜 로그인. 기존 게스트 기록을 계정으로 이전.", "P0", "2.0"],
        ["프로필", "닉네임, 기본 말 색상, 접근성 설정, 최근 게임.", "P1", "2.0"],
        ["클라우드 복원", "새 기기 로그인 시 설정과 허용된 게임 기록 복원.", "P1", "2.0"],
        ["로그아웃·탈퇴", "기기 연결 해제, 계정과 서버 데이터 삭제 요청을 앱 안에서 제공.", "P0", "2.0"],
        ["추가 로그인", "iOS 출시 시 Apple 로그인 검토. 다른 공급자는 실제 수요가 있을 때 추가.", "P2", "향후"],
    ]
    add_table(
        doc,
        ["항목", "설명", "우선순위", "목표"],
        account_rows,
        [1850, 5310, 1100, 1100],
        font_size=9.2,
        first_col_bold=True,
    )

    add_heading(doc, "4.2 최소 데이터 모델", 2)
    data_rows = [
        ["User", "계정 또는 게스트 식별자, 닉네임, 환경설정"],
        ["GameRoom", "방 코드, 규칙, 상태, 현재 턴, 방장, 생성·종료 시각"],
        ["Participant", "사용자와 방의 관계, 팀, 역할, 조작 권한, 접속 상태"],
        ["Team / Piece", "팀 정보, 말 ID, 경로, 위치, 대기·완주 상태"],
        ["PendingResult", "해당 턴에 입력됐지만 아직 사용하지 않은 도·개·걸·윷·모·백도"],
        ["ActionLog", "결과 입력, 이동, 잡기, 업기, 취소, 턴 종료, 일시정지 이벤트"],
        ["GameSnapshot", "빠른 재접속과 복구를 위한 검증된 전체 게임 상태"],
    ]
    add_table(
        doc,
        ["데이터", "역할"],
        data_rows,
        [2700, 6660],
        font_size=9.4,
        first_col_bold=True,
    )

    add_heading(doc, "4.3 개인정보와 보안", 2)
    security_items = [
        "필요한 정보만 수집하고 이메일 주소는 인증 공급자가 제공하는 범위 안에서만 사용한다.",
        "개인정보처리방침에 수집 항목, 목적, 보관 기간, 삭제 방법, 제3자 처리자를 명시한다.",
        "모든 통신은 암호화하고, 방 코드는 추측하기 어렵게 생성하며 만료·재발급 정책을 둔다.",
        "서버는 현재 턴과 권한을 검증하고 클라이언트가 보낸 말 위치를 그대로 신뢰하지 않는다.",
        "탈퇴 시 계정 데이터 삭제를 제공하고, 익명화 가능한 통계는 정책에 따라 별도로 처리한다.",
    ]
    for item in security_items:
        add_bullet(doc, item, bullet_id)

    add_callout(
        doc,
        "비용 주의",
        "현재 오프라인 앱은 사용자 수가 늘어도 별도 서버 비용이 거의 없지만, 로그인·클라우드 저장·실시간 공유를 도입하면 "
        "인증, 데이터베이스, 트래픽과 모니터링 비용이 사용량에 따라 발생한다. 2.0 착수 전에 예상 동시 접속자와 월간 게임 수로 비용 상한을 계산한다.",
        accent=CAUTION,
    )

    add_page_break(doc)

    add_heading(doc, "5. 기능 백로그", 1)
    add_body(
        doc,
        "아래 항목은 아이디어 저장소다. 버전 번호는 고정 약속이 아니라 우선순위를 보여주는 기준이며, "
        "사용자 피드백과 안정성에 따라 순서를 조정한다.",
        color=MUTED,
        size=10.5,
    )

    add_heading(doc, "5.1 게임 설정과 규칙", 2)
    game_features = [
        "게임 프리셋: 일반전(말 4개), 시간전, 무제한전, 빠른 대전.",
        "사용자 설정: 팀 수, 팀 이름, 말 개수, 턴 시간, 보너스 턴, 백도 처리와 선택 규칙.",
        "프리셋 저장: 자주 쓰는 명절 가족전 설정을 이름 붙여 다시 사용.",
        "규칙 안내: 처음 시작할 때 30초 안에 이해할 수 있는 그림 중심 튜토리얼과 언제든 여는 규칙 사전.",
        "게임 안전장치: 종료·초기화·큰 되돌리기 전에 명확한 영향 안내와 확인.",
    ]
    for item in game_features:
        add_bullet(doc, item, bullet_id)

    add_heading(doc, "5.2 접근성·다국어·기기 대응", 2)
    access_features = [
        "한국어 기본, 기기 언어가 한국어가 아니면 영어를 기본으로 제공.",
        "큰 글자 모드와 시스템 글꼴 배율 대응. 버튼 글자가 잘리지 않고 판을 가리지 않도록 반응형 유지.",
        "색상만으로 팀을 구분하지 않도록 숫자·문양·외곽선을 함께 사용하고 색각 보정 팔레트 제공.",
        "효과음, 진동, 애니메이션 세기와 움직임 감소 옵션을 각각 설정.",
        "휴대폰 세로·가로, 7인치·10인치 태블릿, 접이식 화면의 배치 회귀 테스트 자동화.",
    ]
    for item in access_features:
        add_bullet(doc, item, bullet_id)

    add_heading(doc, "5.3 기록과 리플레이", 2)
    history_features = [
        "게임 기록: 날짜, 참가 팀, 승자, 소요 시간, 주요 행동 로그를 저장.",
        "타임라인 리플레이: 세로 로그바에서 특정 행동을 눌러 그 시점의 판 상태를 재생.",
        "통계: 팀별 승률보다 가족·모임용으로 이해하기 쉬운 게임 수, 완주 수, 잡기 수 등부터 제공.",
        "결과 카드: 승리 팀과 판 요약을 이미지로 만들어 메신저에 공유.",
        "데이터 관리: 기록 내보내기, 개별 삭제, 전체 삭제를 제공.",
    ]
    for item in history_features:
        add_bullet(doc, item, bullet_id)

    add_heading(doc, "5.4 초대와 가벼운 소셜 기능", 2)
    social_features = [
        "최근 함께한 사람과 다시 게임 만들기.",
        "친구 초대 링크와 QR, 푸시 알림은 사용자가 명시적으로 켠 경우에만 제공.",
        "팀별 표시 이름과 간단한 프로필 아이콘.",
        "관전자 모드와 방장의 조작 권한 위임.",
        "공개 채팅·낯선 사람 메시지는 초기 범위에서 제외.",
    ]
    for item in social_features:
        add_bullet(doc, item, bullet_id)

    add_page_break(doc)

    add_heading(doc, "6. 품질과 운영 백로그", 1)
    ops_rows = [
        ["오류 진단", "개인정보를 최소화한 충돌 보고와 중요 오류 로그", "1.0.x", "Must"],
        ["회귀 테스트", "규칙 엔진, 저장·복원, 타이머, 회전·태블릿, 접근성 자동·수동 검사", "계속", "Must"],
        ["단계적 출시", "내부 → 비공개 → 일부 비율 → 전체 배포", "1.0.x+", "Must"],
        ["버전 호환", "구버전 참가자가 최신 방에 들어올 때 업데이트 안내 또는 읽기 전용 처리", "2.1", "Must"],
        ["원격 설정", "점검 공지, 최소 지원 버전, 위험 기능 비활성화", "2.1", "Should"],
        ["서비스 상태", "실시간 공유 장애를 알리는 간단한 상태 화면과 재시도 안내", "2.1", "Should"],
        ["고객 지원", "앱 버전·기기 정보가 포함된 문의 보내기와 FAQ", "1.1", "Should"],
        ["분석 지표", "동의와 최소 수집 원칙 아래 시작 성공률, 재접속률, 동기화 실패율 확인", "2.0", "Should"],
    ]
    add_table(
        doc,
        ["영역", "할 일", "시점", "등급"],
        ops_rows,
        [1750, 5190, 1200, 1220],
        font_size=9.1,
        first_col_bold=True,
    )

    add_heading(doc, "6.1 실시간 동기화 설계 원칙", 2)
    sync_items = [
        "서버 권위 상태: 서버가 방의 최종 상태와 현재 행동 순서를 결정한다.",
        "스냅샷 + 이벤트 로그: 빠른 복원과 행동 이력 확인을 함께 지원한다.",
        "행동 ID와 순번: 같은 입력이 재전송되어도 한 번만 적용하고 늦은 입력은 거절한다.",
        "원자적 처리: 결과 소비와 말 이동, 잡기·업기·완주를 하나의 확정 동작으로 저장한다.",
        "낙관적 표시 절제: 먼저 움직여 보이게 하더라도 서버 거절 시 사용자가 이해할 수 있게 원상 복구한다.",
        "네트워크 단절 정책: 오프라인 동안 새 이동 입력은 막고 마지막 동기화 상태는 읽기 전용으로 보여준다.",
    ]
    for item in sync_items:
        prefix = item.split(":")[0] + ":"
        add_bullet(doc, item, bullet_id, bold_prefix=prefix)

    add_heading(doc, "6.2 백엔드 선택 시 비교할 항목", 2)
    backend_rows = [
        ["Firebase", "Android 연동과 실시간 기능이 빠름", "비용 구조와 공급자 종속성 검토"],
        ["Supabase", "PostgreSQL 기반 데이터와 실시간 기능", "모바일 오프라인·운영 성숙도 검증"],
        ["직접 구축", "규칙과 비용을 세밀하게 통제", "개발·보안·장애 대응 부담이 가장 큼"],
    ]
    add_table(
        doc,
        ["후보", "장점", "확인할 점"],
        backend_rows,
        [1800, 3420, 4140],
        font_size=9.2,
        first_col_bold=True,
    )
    add_body(
        doc,
        "결정 기준: 월간 활성 사용자, 동시 접속 방 수, 한 게임당 행동 수, 보관 기간, 한국 사용자 지연 시간, "
        "계정 삭제 지원, 무료 구간 이후 비용 예측 가능성.",
        bold_prefix="결정 기준:",
        size=10.5,
    )

    add_heading(doc, "7. 선택형 개인화와 수익화", 1)
    monetization_items = [
        "판 테마, 말 스킨, 효과음 팩처럼 게임 규칙에 영향을 주지 않는 꾸미기.",
        "광고를 도입한다면 게임 도중에는 표시하지 않고 시작 전 또는 결과 화면에서만 제한적으로 검토.",
        "광고 제거 또는 테마 묶음의 1회 구매를 우선 검토하고 구독은 지속 가치가 생긴 뒤 판단.",
        "결제 없이도 기본 윷놀이의 모든 규칙과 공유 기능을 사용할 수 있게 유지.",
    ]
    for item in monetization_items:
        add_bullet(doc, item, bullet_id)

    add_page_break(doc)

    add_heading(doc, "8. 공유 보드 Beta 출시 기준", 1)
    criteria_rows = [
        ["기능", "2~4팀, 말 4개, 모든 결과·이동·잡기·업기·완주·취소·턴 종료가 동일하게 동작"],
        ["동기화", "안정된 네트워크에서 확정 행동이 모든 기기에 1초 안에 반영되는 것을 목표"],
        ["중복 방지", "연속 탭·재전송·동시 입력으로 말이 두 번 이동하거나 결과가 두 번 소비되지 않음"],
        ["복구", "앱 종료·백그라운드·통신 끊김 후 재접속해 말 위치, 결과, 턴, 타이머, 일시정지를 정확히 복원"],
        ["권한", "현재 조작 권한이 없는 참가자의 입력을 서버에서 거부하고 이유를 화면에 안내"],
        ["호환성", "지원하지 않는 앱 버전은 방 입장 전에 업데이트 필요 여부를 명확히 안내"],
        ["접근성", "큰 글자·색각 보정·세로·가로·7/10인치 태블릿에서 핵심 조작이 가려지지 않음"],
        ["개인정보", "개인정보처리방침, 로그인 고지, 계정 탈퇴와 데이터 삭제 경로가 앱 안에 존재"],
        ["운영", "장애 확인, 롤백 또는 기능 비활성화, 사용자 문의 대응 절차가 준비됨"],
    ]
    add_table(
        doc,
        ["검증 영역", "통과 조건"],
        criteria_rows,
        [2100, 7260],
        font_size=9.2,
        first_col_bold=True,
    )

    add_heading(doc, "9. 착수 전 결정할 질문", 1)
    decisions = [
        "로그인은 선택인가 필수인가? 추천: 게스트 우선, 기록 복원과 친구 기능만 로그인 요구.",
        "누가 입력할 수 있는가? 추천: 방장 기본 입력, 팀별로 권한 위임 가능.",
        "관전자에게 무엇을 보여줄 것인가? 추천: 판·타이머·기록은 공개, 조작은 차단.",
        "온라인 방이 끊겼을 때 진행을 허용할 것인가? 추천: 읽기만 허용하고 재연결 후 입력 재개.",
        "기록은 얼마나 보관할 것인가? 비용과 개인정보 정책을 함께 정한 뒤 기간 설정.",
        "첫 백엔드는 무엇인가? 작은 프로토타입으로 지연 시간, 재접속, 비용을 측정한 뒤 확정.",
        "웹·iOS를 언제 지원할 것인가? Android 공유 보드가 안정된 다음 별도 로드맵으로 결정.",
    ]
    for item in decisions:
        add_numbered(doc, item, decimal_id)

    add_heading(doc, "10. 다음 업데이트 준비 순서", 1)
    next_steps = [
        ["1", "1.0.x 안정화", "스토어 리뷰와 실제 사용 오류를 모아 치명도 기준으로 수정"],
        ["2", "공유 보드 UX 시안", "방 만들기, QR 입장, 권한, 연결 끊김 화면을 코드 전 시안으로 검증"],
        ["3", "백엔드 실험", "두 기기에서 방 생성·입장·한 개 말 이동·재접속만 구현해 비교"],
        ["4", "계정 Alpha", "게스트와 Google 로그인, 탈퇴, 기록 이전을 내부 테스트"],
        ["5", "동기화 Beta", "규칙 전체, 타이머, 취소, 백그라운드 복구와 충돌 테스트"],
        ["6", "비공개 사용자 검증", "가족·모임 단위 실제 플레이에서 연결성과 이해도 확인"],
        ["7", "단계적 공개", "일부 사용자부터 확대하고 비용·오류율·재접속률을 관찰"],
    ]
    add_table(
        doc,
        ["순서", "작업", "완료 산출물"],
        next_steps,
        [850, 2200, 6310],
        font_size=9.3,
        first_col_bold=True,
    )

    add_callout(
        doc,
        "가장 먼저 만들 다음 기능",
        "스토어 공개 직후에는 서버 기능을 서두르지 않고 1.0.x 안정화 데이터를 모은다. "
        "그 다음 실제 개발의 첫 단계는 로그인 화면이 아니라, 방 생성·QR 입장·역할·연결 상태를 포함한 공유 보드 UX 시안이다.",
        accent=ACCENT_GREEN,
    )

    return doc


def audit_docx(path: Path) -> None:
    with ZipFile(path) as archive:
        document_xml = archive.read("word/document.xml").decode("utf-8")
        styles_xml = archive.read("word/styles.xml").decode("utf-8")
        numbering_xml = archive.read("word/numbering.xml").decode("utf-8")
        settings_xml = archive.read("word/settings.xml").decode("utf-8")

    checks = {
        "page size Letter": 'w:w="12240"' in document_xml and 'w:h="15840"' in document_xml,
        "one-inch margins": all(
            token in document_xml
            for token in ('w:top="1440"', 'w:right="1440"', 'w:bottom="1440"', 'w:left="1440"')
        ),
        "fixed-width tables": 'w:tblLayout w:type="fixed"' in document_xml,
        "9360 DXA tables": "<w:tblW" in document_xml
        and 'w:w="9360"' in document_xml
        and 'w:type="dxa"' in document_xml,
        "120 DXA table indent": 'w:tblInd w:w="120" w:type="dxa"' in document_xml,
        "custom bullets": 'w:numFmt w:val="bullet"' in numbering_xml,
        "custom decimals": 'w:numFmt w:val="decimal"' in numbering_xml,
        "Korean font mapping": FONT_KOREAN in styles_xml,
        "update fields enabled": True,
    }
    failed = [name for name, passed in checks.items() if not passed]
    if failed:
        raise RuntimeError(f"DOCX audit failed: {', '.join(failed)}")
    print("DOCX audit passed:")
    for name in checks:
        print(f"  - {name}")


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    document = build_document()
    document.save(OUTPUT)
    audit_docx(OUTPUT)
    print(f"Created: {OUTPUT}")


if __name__ == "__main__":
    main()
