/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.core.journal.impl;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.hornetq.api.core.HornetQBuffer;
import org.hornetq.api.core.HornetQBuffers;
import org.hornetq.api.core.HornetQExceptionType;
import org.hornetq.core.journal.EncodingSupport;
import org.hornetq.core.journal.IOAsyncTask;
import org.hornetq.core.journal.SequentialFile;
import org.hornetq.core.journal.SequentialFileFactory;
import org.hornetq.journal.HornetQJournalBundle;
import org.hornetq.journal.HornetQJournalLogger;

/**
 * A AbstractSequentialFile
 *
 * @author <mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 *
 *
 */
abstract class AbstractSequentialFile implements SequentialFile
{

   // Constants -----------------------------------------------------
   // Attributes ----------------------------------------------------

   private File file;

   private final String directory;

   protected final SequentialFileFactory factory;

   protected long fileSize = 0;

   protected final AtomicLong position = new AtomicLong(0);

   protected TimedBuffer timedBuffer;

   /** Instead of having AIOSequentialFile implementing the Observer, I have done it on an inner class.
    *  This is the class returned to the factory when the file is being activated. */
   protected final TimedBufferObserver timedBufferObserver = new LocalBufferObserver();

   /** Used for asynchronous writes */
   protected final Executor writerExecutor;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   /**
    * @param file
    * @param directory
    */
   public AbstractSequentialFile(final String directory,
                                 final File file,
                                 final SequentialFileFactory factory,
                                 final Executor writerExecutor)
   {
      super();
      this.file = file;
      this.directory = directory;
      this.factory = factory;
      this.writerExecutor = writerExecutor;
   }

   // Public --------------------------------------------------------

   public final boolean exists()
   {
      return file.exists();
   }

   public final String getFileName()
   {
      return file.getName();
   }

   public final void delete() throws Exception
   {
      if (isOpen())
      {
         close();
      }

      if (file.exists() && !file.delete())
      {
         HornetQJournalLogger.LOGGER.errorDeletingFile(this);
      }
   }

   public void copyTo(SequentialFile newFileName) throws Exception
   {
      HornetQJournalLogger.LOGGER.debug("Copying "  + this + " as " + newFileName);
      newFileName.open();
      if (!isOpen())
      {
         this.open();
      }


      ByteBuffer buffer = ByteBuffer.allocate(10 * 1024);

      for (;;)
      {
         buffer.rewind();
         int size = this.read(buffer);
         newFileName.writeInternal(buffer);
         if (size < 10 * 1024)
         {
            break;
         }
      }
      newFileName.close();
      this.close();
   }

   public void position(final long pos) throws Exception
   {
      position.set(pos);
   }

   public long position() throws Exception
   {
      return position.get();
   }

   public final void renameTo(final String newFileName) throws Exception
   {
      close();
      File newFile = new File(directory + "/" + newFileName);

      if (!file.equals(newFile))
      {
         if (!file.renameTo(newFile))
         {
            throw HornetQJournalBundle.BUNDLE.ioRenameFileError(file.getName(), newFileName);
         }
         file = newFile;
      }
   }

   public synchronized void close() throws Exception
   {
      final CountDownLatch donelatch = new CountDownLatch(1);

      if (writerExecutor != null)
      {
         writerExecutor.execute(new Runnable()
         {
            public void run()
            {
               donelatch.countDown();
            }
         });

         while (!donelatch.await(60, TimeUnit.SECONDS))
         {
            HornetQJournalLogger.LOGGER.couldNotCompleteTask(new Exception("trace"), file.getName());
         }
      }
   }

   public final boolean fits(final int size)
   {
      if (timedBuffer == null)
      {
         return position.get() + size <= fileSize;
      }
      else
      {
         return timedBuffer.checkSize(size);
      }
   }

   public void setTimedBuffer(final TimedBuffer buffer)
   {
      if (timedBuffer != null)
      {
         timedBuffer.setObserver(null);
      }

      timedBuffer = buffer;

      if (buffer != null)
      {
         buffer.setObserver(timedBufferObserver);
      }

   }

   public void write(final HornetQBuffer bytes, final boolean sync, final IOAsyncTask callback) throws Exception
   {
      if (timedBuffer != null)
      {
         bytes.setIndex(0, bytes.capacity());
         timedBuffer.addBytes(bytes, sync, callback);
      }
      else
      {
         ByteBuffer buffer = factory.newBuffer(bytes.capacity());
         buffer.put(bytes.toByteBuffer().array());
         buffer.rewind();
         writeDirect(buffer, sync, callback);
      }
   }

   public void write(final HornetQBuffer bytes, final boolean sync) throws Exception
   {
      if (sync)
      {
         SimpleWaitIOCallback completion = new SimpleWaitIOCallback();

         write(bytes, true, completion);

         completion.waitCompletion();
      }
      else
      {
         write(bytes, false, DummyCallback.getInstance());
      }
   }

   public void write(final EncodingSupport bytes, final boolean sync, final IOAsyncTask callback) throws Exception
   {
      if (timedBuffer != null)
      {
         timedBuffer.addBytes(bytes, sync, callback);
      }
      else
      {
         ByteBuffer buffer = factory.newBuffer(bytes.getEncodeSize());

         // If not using the TimedBuffer, a final copy is necessary
         // Because AIO will need a specific Buffer
         // And NIO will also need a whole buffer to perform the write

         HornetQBuffer outBuffer = HornetQBuffers.wrappedBuffer(buffer);
         bytes.encode(outBuffer);
         buffer.rewind();
         writeDirect(buffer, sync, callback);
      }
   }

   public void write(final EncodingSupport bytes, final boolean sync) throws Exception
   {
      if (sync)
      {
         SimpleWaitIOCallback completion = new SimpleWaitIOCallback();

         write(bytes, true, completion);

         completion.waitCompletion();
      }
      else
      {
         write(bytes, false, DummyCallback.getInstance());
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected File getFile()
   {
      return file;
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   protected static class DelegateCallback implements IOAsyncTask
   {
      final List<IOAsyncTask> delegates;

      DelegateCallback(final List<IOAsyncTask> delegates)
      {
         this.delegates = delegates;
      }

      public void done()
      {
         for (IOAsyncTask callback : delegates)
         {
            try
            {
               callback.done();
            }
            catch (Throwable e)
            {
               HornetQJournalLogger.LOGGER.errorCompletingCallback(e);
            }
         }
      }

      public void onError(final int errorCode, final String errorMessage)
      {
         for (IOAsyncTask callback : delegates)
         {
            try
            {
               callback.onError(errorCode, errorMessage);
            }
            catch (Throwable e)
            {
               HornetQJournalLogger.LOGGER.errorCallingErrorCallback(e);
            }
         }
      }
   }

   protected ByteBuffer newBuffer(int size, int limit)
   {
      size = factory.calculateBlockSize(size);
      limit = factory.calculateBlockSize(limit);

      ByteBuffer buffer = factory.newBuffer(size);
      buffer.limit(limit);
      return buffer;
   }

   protected class LocalBufferObserver implements TimedBufferObserver
   {
      public void flushBuffer(final ByteBuffer buffer, final boolean requestedSync, final List<IOAsyncTask> callbacks)
      {
         buffer.flip();

         if (buffer.limit() == 0)
         {
            factory.releaseBuffer(buffer);
         }
         else
         {
            writeDirect(buffer, requestedSync, new DelegateCallback(callbacks));
         }
      }

      public ByteBuffer newBuffer(final int size, final int limit)
      {
         return AbstractSequentialFile.this.newBuffer(size, limit);
      }

      public int getRemainingBytes()
      {
         if (fileSize - position.get() > Integer.MAX_VALUE)
         {
            return Integer.MAX_VALUE;
         }
         else
         {
            return (int)(fileSize - position.get());
         }
      }

      @Override
      public String toString()
      {
         return "TimedBufferObserver on file (" + getFile().getName() + ")";
      }

   }

   @Override
   public File getJavaFile()
   {
      return getFile().getAbsoluteFile();
   }
}
