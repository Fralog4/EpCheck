from fpdf import FPDF

pdf = FPDF()
pdf.add_page()
pdf.set_font("Arial", size=12)
text = """FLIGHT LOG SUMMARY - DATE: 2002-05-10
Aircraft: N908JE
Passengers: Kevin Spacey, Ghislaine Maxwell.
Destination: Little St. James."""

for line in text.split('\n'):
    pdf.cell(200, 10, txt=line, ln=1, align='L')

pdf.output("test_flight_log.pdf")
print("PDF generated successfully.")
