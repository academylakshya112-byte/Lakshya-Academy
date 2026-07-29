import re

with open('app/src/main/java/com/example/ui/screens/StudentR2VideosScreen.kt', 'r') as f:
    content = f.read()

target = r'''                                                    if (isLocked) {
                                                        Text(
                                                            text = "Sequential 🔒",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Red,
                                                            modifier = Modifier
                                                                .padding(start = 8.dp)
                                                                .background(Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }'''

replacement = r'''                                                    if (isLocked) {
                                                        Text(
                                                            text = "Sequential 🔒",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Red,
                                                            modifier = Modifier
                                                                .padding(start = 8.dp)
                                                                .background(Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (!item.pdfUrl.isNullOrBlank()) {
                                                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                                                    TextButton(
                                                        onClick = {
                                                            selectedPdfToView = item.copy(videoUrl = item.pdfUrl, title = item.title + " (Notes)")
                                                        },
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("View Notes / Download PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }'''

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/screens/StudentR2VideosScreen.kt', 'w') as f:
    f.write(content)
