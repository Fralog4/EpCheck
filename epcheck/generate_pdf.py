from fpdf import FPDF

pdf = FPDF()

# Page 1: Standard format with labeled fields
pdf.add_page()
pdf.set_font("Arial", size=12)
text1 = """FLIGHT LOG SUMMARY - DATE: 2002-05-10
Aircraft: N908JE
Origin: Teterboro Airport
Destination: Little St. James
Passengers: Kevin Spacey, Ghislaine Maxwell.
Notes: Routine trip, no anomalies reported."""

for line in text1.split('\n'):
    pdf.cell(200, 10, txt=line, ln=1, align='L')

# Page 2: Different format with From/To and US date
pdf.add_page()
pdf.set_font("Arial", size=12)
text2 = """FLIGHT RECORD
Date: 01/22/2003
Tail#: N256BA
From: Palm Beach International
To: Columbus, Ohio
Passengers: Bill Clinton, Secret Service detail.
Duration: 3h 45m"""

for line in text2.split('\n'):
    pdf.cell(200, 10, txt=line, ln=1, align='L')

# Page 3: Minimal format with inline date
pdf.add_page()
pdf.set_font("Arial", size=12)
text3 = """Log entry 2004-11-15.
Cessna N908JE departed Miami for Nassau.
Departure: Miami Executive Airport
Arrival: Nassau, Bahamas
Manifest: Prince Andrew, Virginia Giuffre."""

for line in text3.split('\n'):
    pdf.cell(200, 10, txt=line, ln=1, align='L')

pdf.output("test_flight_log.pdf")
print("PDF generated successfully: test_flight_log.pdf (3 pages)")
