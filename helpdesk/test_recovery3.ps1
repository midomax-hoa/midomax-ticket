$file = 'd:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\dashboard.html'
$text = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
$encoding1252 = [System.Text.Encoding]::GetEncoding(1252)
$bytes = $encoding1252.GetBytes($text)
$restored = [System.Text.Encoding]::UTF8.GetString($bytes)
[System.IO.File]::WriteAllText('d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\restored2.txt', $restored, [System.Text.Encoding]::UTF8)
