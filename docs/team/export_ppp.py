"""Export the five individual PPP summaries as PDFs with a two-page limit.

Run from any directory with reportlab and pypdf installed. Generated files go to
the repository's output/pdf directory; source Markdown remains authoritative.
"""

from html import escape
from pathlib import Path
import re
from urllib.parse import urljoin

from pypdf import PdfReader
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, PageBreak


MEMBERS = ('doyk0215', 'lulu-wen', 'yamyamui', 'yuncongshen', 'zephtzk')
BASE_URL = 'https://ay2627s1-cs2113-w13-3.github.io/tp/team/'


def inline(text):
    """Convert the limited inline Markdown used by the PPPs to PDF markup."""
    text = escape(text, quote=True)
    text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)',
                  lambda match: '<a color="#176080" href="'
                  + urljoin(BASE_URL, match[2]) + '">' + match[1] + '</a>', text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    return re.sub(r'`([^`]+)`', r'<font name="Courier">\1</font>', text)


def blocks(source):
    """Join wrapped lines while retaining headings and individual list entries."""
    current = []
    for line in source.splitlines():
        if not line.strip() or re.match(r'^\s*\* ', line) or line.startswith('#'):
            if current:
                yield ' '.join(current), indent
                current = []
        if line.strip():
            if not current:
                indent = len(line) - len(line.lstrip())
            current.append(line.strip())
    if current:
        yield ' '.join(current), indent


def export(member, source_dir, output_dir):
    """Render a readable summary and reject exports beyond the course limit."""
    body = ParagraphStyle('body', fontName='Helvetica', fontSize=10,
                          leading=12.4, spaceAfter=5, textColor=colors.HexColor('#202b33'))
    title = ParagraphStyle('title', parent=body, fontName='Helvetica-Bold',
                           fontSize=16, leading=19, spaceAfter=11)
    heading = ParagraphStyle('heading', parent=body, fontName='Helvetica-Bold',
                             fontSize=11, leading=14, spaceBefore=7,
                             spaceAfter=5, keepWithNext=True)
    story = []
    for block, indent in blocks((source_dir / (member + '.md')).read_text(encoding='utf-8')):
        # Keep guide, team and review evidence together on a balanced second page.
        if member in ('doyk0215', 'yamyamui', 'zephtzk') and (
                block == '**Contributions to the UG:**'
                or block == '### Contributions to the UG'):
            story.append(PageBreak())
        if block.startswith('# '):
            story.append(Paragraph(inline(block[2:]), title))
        elif block.startswith('#'):
            story.append(Paragraph(inline(block.lstrip('# ')), heading))
        elif block.startswith('* '):
            bullet = ParagraphStyle('bullet', parent=body, leftIndent=indent * 4 + 8,
                                   firstLineIndent=-8)
            story.append(Paragraph('&#8226; ' + inline(block[2:]), bullet))
        elif block.startswith('**') and block.endswith(':**'):
            story.append(Paragraph(inline(block), heading))
        else:
            story.append(Paragraph(inline(block), body))
    target = output_dir / (member + '.pdf')
    def footer(canvas, document):
        canvas.setFont('Helvetica', 8)
        canvas.setFillColor(colors.HexColor('#62717c'))
        canvas.drawString(40, 24, 'LabLog | Project Portfolio')
        canvas.drawRightString(A4[0] - 40, 24, str(document.page))
    document = SimpleDocTemplate(str(target), pagesize=A4, rightMargin=40,
                                 leftMargin=40, topMargin=36, bottomMargin=38,
                                 title=member + ' - LabLog Project Portfolio',
                                 author=member)
    document.build(story, onFirstPage=footer, onLaterPages=footer)
    reader = PdfReader(target)
    if len(reader.pages) > 2:
        raise ValueError(f'{member}: {len(reader.pages)} pages exceeds the two-page limit')
    print(f'{target}: {len(reader.pages)} pages')


if __name__ == '__main__':
    source_dir = Path(__file__).resolve().parent
    output_dir = source_dir.parents[1] / 'output' / 'pdf'
    output_dir.mkdir(parents=True, exist_ok=True)
    for member in MEMBERS:
        export(member, source_dir, output_dir)
