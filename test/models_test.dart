import 'package:flutter_test/flutter_test.dart';
import 'package:distraction_killa/models.dart';

void main() {
  test('fmtDuration', () {
    expect(fmtDuration(const Duration(seconds: 20)), '<1m');
    expect(fmtDuration(const Duration(minutes: 45)), '45m');
    expect(fmtDuration(const Duration(hours: 2)), '2h');
    expect(fmtDuration(const Duration(hours: 2, minutes: 14)), '2h 14m');
  });

  test('nights: 7 windows from 9 PM to 9 AM, clipped to now', () {
    final now = DateTime(2026, 11, 24, 23, 0); // 11 PM, tonight's window has started
    final r = Span.nights.ranges(now);
    expect(r.length, 7);
    expect(r.first.start, DateTime(2026, 11, 24, 21));
    expect(r.first.end, now);
    expect(r[1].start, DateTime(2026, 11, 23, 21));
    expect(r[1].end, DateTime(2026, 11, 24, 9));
  });

  test('nights at 3 PM: last night is the most recent window', () {
    final now = DateTime(2026, 11, 24, 15, 0);
    final r = Span.nights.ranges(now);
    expect(r.length, 7);
    expect(r.first.start, DateTime(2026, 11, 23, 21));
    expect(r.first.end, DateTime(2026, 11, 24, 9));
  });

  test('fmtDateTime', () {
    expect(fmtDateTime(DateTime(2026, 11, 24, 21, 8)), 'Nov 24, 9:08 PM');
    expect(fmtDateTime(DateTime(2026, 1, 3, 0, 30)), 'Jan 3, 12:30 AM');
    expect(fmtDateTime(DateTime(2026, 1, 3, 12, 0)), 'Jan 3, 12:00 PM');
  });
}
