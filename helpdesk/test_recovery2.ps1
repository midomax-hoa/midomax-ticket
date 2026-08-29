$file = 'd:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\src\main\resources\templates\dashboard.html'
$text = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
$bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
$restored = [System.Text.Encoding]::Default.GetString($bytes)
[System.IO.File]::WriteAllText('d:\Midomax\MIDOMAX PROJECT\helpdesk\helpdesk\restored.txt', $restored, [System.Text.Encoding]::UTF8)
